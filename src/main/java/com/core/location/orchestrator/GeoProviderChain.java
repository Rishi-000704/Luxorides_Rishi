package com.core.location.orchestrator;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

import com.core.location.api.DistanceTimeResult;
import com.core.location.provider.GeoProvider;
import com.core.models.embedded.AddressSnapshot;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class GeoProviderChain {

	private final List<GeoProvider> providers;

	/*
	 * Phase B -- these three were previously plain unbounded
	 * ConcurrentHashMaps: correct while the app runs, but their key space is
	 * every distinct address/place-id/lat-lng this org's users ever enter,
	 * unbounded over the process's lifetime -- pure growth, no eviction.
	 *
	 * All three are Category 1 (static/reference geographic data, exactly
	 * this task's own examples): what state or city a location is in, and
	 * whether it's an airport, does not change on any timescale this app's
	 * caching could plausibly go stale against -- administrative boundaries
	 * and airport locations shift over years, not days. So the 30-day TTL
	 * below is not tracking data volatility the way RouteCacheService's 24h
	 * TTL tracks route/traffic conditions; it exists purely as a self-heal
	 * window against a provider data correction or a future
	 * normalization/geocoding logic change, long enough that the cache stays
	 * genuinely effective between deploys, short enough to recover within a
	 * month without needing an eviction job. maxSize=5000 per cache is a
	 * deliberately generous, reasoned bound (each entry is one short string
	 * or boolean, so even 5000 is a trivial memory footprint) rather than a
	 * measured working-set size -- no production traffic data exists yet to
	 * derive a precise number from.
	 */
	private static final int MAX_CACHE_ENTRIES = 5000;
	private static final Duration REFERENCE_DATA_TTL = Duration.ofDays(30);

	private final BoundedTtlCache<String, String> stateCache = new BoundedTtlCache<>(MAX_CACHE_ENTRIES, REFERENCE_DATA_TTL);
	private final BoundedTtlCache<String, String> cityCache = new BoundedTtlCache<>(MAX_CACHE_ENTRIES, REFERENCE_DATA_TTL);
	private final BoundedTtlCache<String, Boolean> airportCache = new BoundedTtlCache<>(MAX_CACHE_ENTRIES, REFERENCE_DATA_TTL);

	public GeoProviderChain(List<GeoProvider> providers) {
		this.providers = providers;
	}

	public boolean isAirport(AddressSnapshot address) {
		String cacheKey = airportCacheKey(address);

		if (cacheKey == null) {
			return isAirportWithoutCache(address);
		}

		return airportCache.computeIfAbsent(
				cacheKey,
				key -> isAirportWithoutCache(address)
		);
	}

	private boolean isAirportWithoutCache(AddressSnapshot address) {
		for (GeoProvider provider : providers) {
			try {
				if (provider.isAirport(address)) {
					return true;
				}
			} catch (Exception ex) {
				logProviderFailure(
						provider,
						"airport check",
						ex
				);
			}
		}

		return false;
	}

	public DistanceTimeResult calculateDistance(
			AddressSnapshot source,
			AddressSnapshot destination
	) {
		for (GeoProvider provider : providers) {
			try {
				DistanceTimeResult result =
						provider.calculateDistanceAndTime(
								source,
								destination
						);

				if (result != null) {
					if (result.estimated()) {
						log.info(
								"Geo provider {} returned estimated distance: {} km, {} sec",
								provider.getName(),
								result.distanceKm(),
								result.durationSeconds()
						);
					}

					return result;
				}
			} catch (Exception ex) {
				logProviderFailure(
						provider,
						"distance calculation",
						ex
				);
			}
		}

		throw new IllegalStateException(
				"All geo providers failed"
		);
	}

	public String resolveState(AddressSnapshot address) {
		if (address == null) {
			throw new IllegalArgumentException(
					"Address is required for state resolution"
			);
		}

		String cacheKey = stateCacheKey(address);

		if (cacheKey == null) {
			throw new IllegalArgumentException(
					"State resolution requires a place ID, valid coordinates, or formatted address"
			);
		}

		return stateCache.computeIfAbsent(
				cacheKey,
				key -> resolveStateWithoutCache(address)
		);
	}

	private String resolveStateWithoutCache(AddressSnapshot address) {
		for (GeoProvider provider : providers) {
			try {
				String state = provider.resolveState(address);

				if (state != null && !state.isBlank()) {
					return state.trim().toUpperCase(Locale.ROOT);
				}
			} catch (Exception ex) {
				logProviderFailure(
						provider,
						"state resolution",
						ex
				);
			}
		}

		throw new IllegalStateException(
				"All geo providers failed to resolve state"
		);
	}

	public String resolveCity(AddressSnapshot address) {
		if (address == null) {
			return null;
		}

		String cacheKey = cityCacheKey(address);

		if (cacheKey == null) {
			return resolveCityWithoutCache(address);
		}

		return cityCache.computeIfAbsent(
				cacheKey,
				key -> resolveCityWithoutCache(address)
		);
	}

	private String resolveCityWithoutCache(AddressSnapshot address) {
		for (GeoProvider provider : providers) {
			try {
				String city = provider.resolveCity(address);

				if (city != null && !city.isBlank()) {
					return city;
				}
			} catch (Exception ex) {
				logProviderFailure(
						provider,
						"city resolution",
						ex
				);
			}
		}

		return null;
	}

	@SuppressWarnings("null")
	private void logProviderFailure(
			GeoProvider provider,
			String operation,
			Exception ex
	) {
		Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);

		if (root instanceof CallNotPermittedException) {
			log.info(
					"Geo provider {} skipped for {} because circuit breaker is open",
					provider.getName(),
					operation
			);

			log.debug(
					"Circuit breaker stack trace",
					ex
			);

			return;
		}

		log.warn(
				"Geo provider {} failed during {}: {}",
				provider.getName(),
				operation,
				root != null
						? root.getMessage()
						: ex.getMessage()
		);

		log.debug(
				"Geo provider failure stack trace",
				ex
		);
	}

	private String airportCacheKey(AddressSnapshot address) {
		if (address == null) {
			return null;
		}

		if (hasText(address.getGooglePlaceId())) {
			return "place:" + address.getGooglePlaceId().trim();
		}

		if (address.getLatitude() != null
				&& address.getLongitude() != null) {

			return "latlng:"
					+ round(address.getLatitude())
					+ ","
					+ round(address.getLongitude());
		}

		if (hasText(address.getFormattedAddress())) {
			return "addr:"
					+ normalizeAddress(address.getFormattedAddress());
		}

		return null;
	}

	private String stateCacheKey(AddressSnapshot address) {
		if (address == null) {
			return null;
		}

		StringBuilder key = new StringBuilder();

		if (hasText(address.getGooglePlaceId())) {
			key.append("place:")
					.append(address.getGooglePlaceId().trim());
		}

		if (hasValidLatLng(address)) {
			appendCacheKeySeparator(key);

			key.append("latlng:")
					.append(round(address.getLatitude()))
					.append(',')
					.append(round(address.getLongitude()));
		}

		if (hasText(address.getFormattedAddress())) {
			appendCacheKeySeparator(key);

			key.append("addr:")
					.append(
							normalizeAddress(
									address.getFormattedAddress()
							)
					);
		}

		return key.length() == 0
				? null
				: key.toString();
	}

	private String cityCacheKey(AddressSnapshot address) {
		if (address == null) {
			return null;
		}

		if (hasText(address.getGooglePlaceId())) {
			return "place:" + address.getGooglePlaceId().trim();
		}

		if (hasText(address.getFormattedAddress())) {
			return "addr:"
					+ normalizeAddress(address.getFormattedAddress());
		}

		return null;
	}

	private void appendCacheKeySeparator(StringBuilder key) {
		if (key.length() > 0) {
			key.append('|');
		}
	}

	private boolean hasValidLatLng(AddressSnapshot address) {
		if (address == null
				|| address.getLatitude() == null
				|| address.getLongitude() == null) {

			return false;
		}

		double latitude = address.getLatitude();
		double longitude = address.getLongitude();

		return Double.isFinite(latitude)
				&& Double.isFinite(longitude)
				&& latitude >= -90
				&& latitude <= 90
				&& longitude >= -180
				&& longitude <= 180;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String normalizeAddress(String value) {
		return value.trim()
				.replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT);
	}

	private double round(Double value) {
		return Math.round(value * 100000.0) / 100000.0;
	}
}
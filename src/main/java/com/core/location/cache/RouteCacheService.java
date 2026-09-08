package com.core.location.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.core.location.api.DistanceTimeResult;
import com.core.location.api.GeoPoint;
import com.core.models.RouteCacheEntry;
import com.core.models.embedded.AddressSnapshot;
import com.core.repositories.RouteCacheEntryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * Cost-aware route reuse (routing audit Phase 2B/2C). Sits between
 * LocationServiceImpl and GeoProviderChain: on a fresh cache hit, no external
 * provider call is made at all; on a miss, concurrent requests for the exact
 * same route on this instance are coalesced into one live call (single-
 * flight), and only a genuine (non-estimated) provider result is persisted
 * for reuse by this and every other application instance.
 *
 * Never authoritative for anything live: this only ever answers "what did a
 * real provider last say this fixed route's distance/duration/geometry is",
 * gated by a time-based freshness window -- it never touches GPS position,
 * duty status, payment, OTP, or authorization, and a caller that needs
 * genuinely live routing (see EtaEstimator's haversine-from-live-GPS, which
 * deliberately bypasses this whole chain) must not be routed through here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RouteCacheService {

	private static final String PROFILE = "driving-car";

	/*
	 * Neither ORS nor the Google Distance Matrix call this codebase makes
	 * requests live-traffic-aware duration (no departure_time/traffic_model
	 * param) -- so there is no "current conditions" signal for this system to
	 * weigh a cached result against (Phase 2E). Freshness is therefore purely
	 * time-based. 24h is a deliberate, disclosed choice: generous enough to
	 * cover the two duplication patterns this exists for (repeated
	 * validateVehicle calls within one shopping session; a driver's
	 * navigation screen remounting during one duty), short enough that a
	 * road/infrastructure change or a future routing-logic revision self-heals
	 * within a day without needing an eviction job.
	 */
	private static final Duration FRESHNESS_TTL = Duration.ofHours(24);

	private final RouteCacheEntryRepository repository;
	private final ObjectMapper objectMapper;

	/*
	 * In-process only (Phase 2C): coalesces concurrent identical requests on
	 * THIS instance so they fire one live provider call instead of N. Entries
	 * are removed as soon as their computation finishes, so this never grows
	 * unbounded and never serves a result older than the request that
	 * triggered it -- reuse across time/instances is the persistent cache's
	 * job, not this map's.
	 */
	private final ConcurrentHashMap<String, CompletableFuture<DistanceTimeResult>> inFlight = new ConcurrentHashMap<>();

	public DistanceTimeResult getOrCompute(
			AddressSnapshot source,
			AddressSnapshot destination,
			Supplier<DistanceTimeResult> liveComputation
	) {
		String originKey = cacheKey(source);
		String destinationKey = cacheKey(destination);

		if (originKey == null || destinationKey == null) {
			return liveComputation.get();
		}

		Optional<RouteCacheEntry> fresh = readFresh(originKey, destinationKey);

		if (fresh.isPresent()) {
			// Within FRESHNESS_TTL -- trustworthy enough to stand in for a live
			// call, including for billing/pricing decisions downstream.
			return toResult(fresh.get(), false);
		}

		String coalesceKey = originKey + ">" + destinationKey;

		return computeWithCoalescing(coalesceKey, () -> {
			try {
				DistanceTimeResult live = liveComputation.get();

				if (!live.estimated()) {
					saveOrUpdate(originKey, destinationKey, live);
				}

				return live;
			} catch (RuntimeException liveComputationFailure) {
				/*
				 * GeoProviderChain already falls through ORS -> Google ->
				 * Haversine, so reaching here means every provider, including
				 * the always-available haversine estimate, failed -- an extreme
				 * case (e.g. a misconfigured provider list). Serving a stale
				 * (past-TTL) cached result here is a deliberate, disclosed
				 * degraded-mode fallback -- "something beats nothing" for a
				 * non-financial, non-safety display value -- not a claim that
				 * stale data is authoritative; it is only ever used when live
				 * computation has just demonstrably failed, and the fact that a
				 * fallback was used is logged.
				 */
				Optional<RouteCacheEntry> stale = readAnyExisting(originKey, destinationKey);

				if (stale.isPresent()) {
					log.warn("Live route computation failed for {}->{}, serving stale cached result from {}: {}",
							originKey, destinationKey, stale.get().getCalculatedAt(),
							liveComputationFailure.getMessage());

					// P0 financial-integrity fix -- this result is past
					// FRESHNESS_TTL and was only reached because live
					// computation just failed. It must be indistinguishable
					// from any other "estimated" (non-authoritative) result
					// to every downstream caller, exactly like a
					// FallbackGeoProvider/haversine result -- billing-relevant
					// callers (garage-return distance, duty-type/pricing
					// classification) must treat estimated=true as "do not use
					// this to create or increase a customer charge."
					// Previously this hardcoded estimated=false here, making
					// stale data indistinguishable from a genuinely fresh
					// result to any caller checking the flag.
					return toResult(stale.get(), true);
				}

				throw liveComputationFailure;
			}
		});
	}

	private Optional<RouteCacheEntry> readAnyExisting(String originKey, String destinationKey) {
		try {
			return repository.findByOriginKeyAndDestinationKeyAndProfile(originKey, destinationKey, PROFILE);
		} catch (Exception ex) {
			log.warn("Route cache stale-fallback read failed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private Optional<RouteCacheEntry> readFresh(String originKey, String destinationKey) {
		try {
			Optional<RouteCacheEntry> entry = repository
					.findByOriginKeyAndDestinationKeyAndProfile(originKey, destinationKey, PROFILE)
					.filter(this::isFresh);

			entry.ifPresent(this::recordHitBestEffort);

			return entry;
		} catch (Exception ex) {
			log.warn("Route cache read failed, falling back to live computation: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private boolean isFresh(RouteCacheEntry entry) {
		return entry.getCalculatedAt() != null
				&& entry.getCalculatedAt().isAfter(Instant.now().minus(FRESHNESS_TTL));
	}

	private void recordHitBestEffort(RouteCacheEntry entry) {
		try {
			repository.recordHit(entry.getId(), Instant.now());
		} catch (Exception ex) {
			// Usage stats are observability, not correctness -- never let a failure
			// here affect the route result already resolved.
			log.debug("Route cache hit-count update failed for {}: {}", entry.getId(), ex.getMessage());
		}
	}

	private void saveOrUpdate(String originKey, String destinationKey, DistanceTimeResult live) {
		try {
			RouteCacheEntry entry = repository
					.findByOriginKeyAndDestinationKeyAndProfile(originKey, destinationKey, PROFILE)
					.orElseGet(RouteCacheEntry::new);

			entry.setOriginKey(originKey);
			entry.setDestinationKey(destinationKey);
			entry.setProfile(PROFILE);
			entry.setDistanceKm(live.distanceKm());
			entry.setDurationSeconds(live.durationSeconds());
			entry.setProvider(live.provider());
			entry.setRouteGeometryJson(serializeGeometry(live.routeGeometry()));
			entry.setCalculatedAt(Instant.now());

			if (entry.getUsageCount() == null) {
				entry.setUsageCount(0L);
			}

			if (entry.getLastUsedAt() == null) {
				entry.setLastUsedAt(Instant.now());
			}

			repository.save(entry);
		} catch (Exception ex) {
			/*
			 * Best-effort: a write failure (including a unique-constraint clash
			 * from a second instance winning the same cache-miss race
			 * concurrently -- see class comment on RouteCacheEntry) must never
			 * affect the live result already computed and about to be returned
			 * to the caller.
			 */
			log.warn("Route cache write failed (result still returned to caller): {}", ex.getMessage());
		}
	}

	private DistanceTimeResult toResult(RouteCacheEntry entry, boolean estimated) {
		return new DistanceTimeResult(
				entry.getDistanceKm(),
				entry.getDurationSeconds(),
				estimated,
				entry.getProvider(),
				deserializeGeometry(entry.getRouteGeometryJson()));
	}

	private DistanceTimeResult computeWithCoalescing(String key, Supplier<DistanceTimeResult> supplier) {
		CompletableFuture<DistanceTimeResult> myFuture = new CompletableFuture<>();
		CompletableFuture<DistanceTimeResult> existing = inFlight.putIfAbsent(key, myFuture);

		if (existing != null) {
			return joinUnchecked(existing);
		}

		try {
			DistanceTimeResult result = supplier.get();
			myFuture.complete(result);
			return result;
		} catch (RuntimeException ex) {
			myFuture.completeExceptionally(ex);
			throw ex;
		} finally {
			inFlight.remove(key, myFuture);
		}
	}

	private DistanceTimeResult joinUnchecked(CompletableFuture<DistanceTimeResult> future) {
		try {
			return future.join();
		} catch (CompletionException ex) {
			if (ex.getCause() instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}

			throw ex;
		}
	}

	private String cacheKey(AddressSnapshot address) {
		if (address == null || address.getLatitude() == null || address.getLongitude() == null) {
			return null;
		}

		double latitude = address.getLatitude();
		double longitude = address.getLongitude();

		if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
			return null;
		}

		return round(latitude) + "," + round(longitude);
	}

	// Same 5-decimal-place (~1.1m) precision GeoProviderChain already uses for
	// its own state/city/airport cache keys.
	private double round(double value) {
		return Math.round(value * 100000.0) / 100000.0;
	}

	private String serializeGeometry(List<GeoPoint> geometry) {
		if (geometry == null || geometry.isEmpty()) {
			return null;
		}

		try {
			return objectMapper.writeValueAsString(geometry);
		} catch (Exception ex) {
			log.warn("Failed to serialize route geometry for caching: {}", ex.getMessage());
			return null;
		}
	}

	private List<GeoPoint> deserializeGeometry(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}

		try {
			return objectMapper.readValue(json, new TypeReference<List<GeoPoint>>() {
			});
		} catch (Exception ex) {
			log.warn("Failed to deserialize cached route geometry: {}", ex.getMessage());
			return null;
		}
	}
}

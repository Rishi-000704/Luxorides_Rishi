package com.core.location.provider;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.core.location.api.DistanceTimeResult;
import com.core.location.api.GeoPoint;
import com.core.location.provider.dto.OrsDirectionsResponse;
import com.core.models.embedded.AddressSnapshot;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Road distance/duration via OpenRouteService Directions V2, driving-car profile.
 *
 * Ordered ahead of {@link GoogleGeoProvider} so real road distance comes from ORS first;
 * on failure {@link com.core.location.orchestrator.GeoProviderChain} falls through to
 * Google, then to the haversine estimate — same resilience pattern already used for the
 * other providers. This provider only implements distance/time: it does not do place
 * lookups, so airport/state/city resolution intentionally stay on Google.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class OpenRouteServiceGeoProvider implements GeoProvider {

	private final WebClient openRouteServiceWebClient;

	@Value("${ors.api-key}")
	private String apiKey;

	@Value("${geo.http.response-timeout-ms}")
	private long responseTimeoutMs;

	@Override
	public String getName() {
		return "OPEN_ROUTE_SERVICE";
	}

	@Override
	public boolean isAirport(AddressSnapshot address) {
		throw new UnsupportedOperationException(
				"OpenRouteService provider does not support airport detection"
		);
	}

	@Override
	@Retry(name = "orsDistance")
	@CircuitBreaker(
			name = "orsDistance",
			fallbackMethod = "distanceFallback"
	)
	public DistanceTimeResult calculateDistanceAndTime(
			AddressSnapshot source,
			AddressSnapshot destination
	) {
		if (!hasLatLng(source) || !hasLatLng(destination)) {
			throw new IllegalArgumentException(
					"Source and destination must have latitude and longitude"
			);
		}

		String start = String.format(
				Locale.ROOT,
				"%f,%f",
				source.getLongitude(),
				source.getLatitude()
		);

		String end = String.format(
				Locale.ROOT,
				"%f,%f",
				destination.getLongitude(),
				destination.getLatitude()
		);

		OrsDirectionsResponse response =
				openRouteServiceWebClient.get()
						.uri(uri -> uri
								.path("/v2/directions/driving-car")
								.queryParam("start", start)
								.queryParam("end", end)
								.build()
						)
						.header("Authorization", apiKey)
						.retrieve()
						.bodyToMono(OrsDirectionsResponse.class)
						// Bounds the whole request/response wait -- a hung ORS
						// call must fail fast enough for GeoProviderChain to
						// fall through to Google rather than hang indefinitely.
						.timeout(Duration.ofMillis(responseTimeoutMs))
						.block();

		validateResponse(response, start, end);

		var feature = response.features().get(0);
		var summary = feature.properties().summary();

		return new DistanceTimeResult(
				summary.distance() / 1000.0,
				summary.duration().longValue(),
				false,
				getName(),
				extractGeometry(feature)
		);
	}

	/**
	 * Converts ORS's GeoJSON [lon, lat] coordinate pairs into {@link GeoPoint}s.
	 * Never throws -- a missing/malformed geometry just means no road route can be
	 * drawn on the map; distance/duration (already validated above) are unaffected.
	 */
	List<GeoPoint> extractGeometry(OrsDirectionsResponse.Feature feature) {
		if (feature.geometry() == null || feature.geometry().coordinates() == null) {
			return null;
		}

		return feature.geometry().coordinates()
				.stream()
				.filter(coordinate -> coordinate != null && coordinate.size() >= 2)
				.map(coordinate -> new GeoPoint(coordinate.get(1), coordinate.get(0)))
				.toList();
	}

	private void validateResponse(
			OrsDirectionsResponse response,
			String start,
			String end
	) {
		if (response == null) {
			throw new IllegalStateException(
					"OpenRouteService returned null response"
			);
		}

		if (response.error() != null) {
			throw new IllegalStateException(
					"OpenRouteService failed. code="
							+ response.error().code()
							+ ", message="
							+ response.error().message()
							+ ", start="
							+ start
							+ ", end="
							+ end
			);
		}

		if (response.features() == null
				|| response.features().isEmpty()
				|| response.features().get(0).properties() == null
				|| response.features().get(0).properties().summary() == null
				|| response.features().get(0).properties().summary().distance() == null
				|| response.features().get(0).properties().summary().duration() == null) {

			throw new IllegalStateException(
					"OpenRouteService response missing route summary. start="
							+ start
							+ ", end="
							+ end
			);
		}
	}

	@Override
	public String resolveState(AddressSnapshot address) {
		throw new UnsupportedOperationException(
				"OpenRouteService provider does not support state resolution"
		);
	}

	@Override
	public String resolveCity(AddressSnapshot address) {
		throw new UnsupportedOperationException(
				"OpenRouteService provider does not support city resolution"
		);
	}

	private DistanceTimeResult distanceFallback(
			AddressSnapshot source,
			AddressSnapshot destination,
			Throwable ex
	) {
		log.warn(
				"OpenRouteService distance failed, falling back to next geo provider. reason={}",
				ex.getMessage()
		);

		throw new RuntimeException(
				"OpenRouteService distance unavailable",
				ex
		);
	}

	private boolean hasLatLng(AddressSnapshot address) {
		return address != null
				&& address.getLatitude() != null
				&& address.getLongitude() != null;
	}
}

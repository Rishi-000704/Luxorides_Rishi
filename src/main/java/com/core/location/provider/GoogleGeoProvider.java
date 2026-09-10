package com.core.location.provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.core.location.api.DistanceTimeResult;
import com.core.location.provider.dto.GoogleDistanceMatrixResponse;
import com.core.location.provider.dto.GoogleNearbySearchResponse;
import com.core.location.provider.dto.GooglePlaceDetailsResponse;
import com.core.location.util.AddressParsingUtil;
import com.core.models.embedded.AddressSnapshot;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class GoogleGeoProvider implements GeoProvider {

	private final WebClient googleMapsWebClient;

	@Value("${google.maps.api-key}")
	private String apiKey;

	/**
	 * Keep this higher than 2000 because large airport campuses can exceed 2 km
	 * from terminal / parking / pickup coordinates to the airport place pin.
	 *
	 * IMPORTANT:
	 * This radius is now used only as a supporting signal for terminal-like places.
	 * It is NOT used directly to classify a normal nearby locality as airport.
	 */
	@Value("${google.airport.radius-meters:5000}")
	private int airportRadiusMeters;

	@Value("${geo.http.response-timeout-ms}")
	private long responseTimeoutMs;

	@Override
	public String getName() {
		return "GOOGLE_MAPS";
	}

	/*
	 * =========================================================
	 * AIRPORT CHECK
	 * =========================================================
	 */

	@Override
	@Retry(name = "googlePlaces")
	@CircuitBreaker(
			name = "googlePlaces",
			fallbackMethod = "isAirportFallback"
	)
	public boolean isAirport(AddressSnapshot address) {

		if (address == null) {
			return false;
		}

		boolean hasLatLng = hasLatLng(address);

		if (hasText(address.getGooglePlaceId())) {
			GooglePlaceDetailsResponse details = fetchPlaceDetails(
					address.getGooglePlaceId(),
					"types,name,formatted_address,address_components"
			);

			if (isDirectAirportPlace(details)) {
				return true;
			}

			if (hasLatLng
					&& isTerminalLikePlace(details)
					&& isAirportNearby(
					address.getLatitude(),
					address.getLongitude()
			)) {

				return true;
			}
		}

		if (looksLikeStrictAirportText(
				address.getFormattedAddress()
		)) {
			return true;
		}

		if (hasLatLng
				&& looksLikeAirportTerminalText(
				address.getFormattedAddress()
		)
				&& isAirportNearby(
				address.getLatitude(),
				address.getLongitude()
		)) {

			return true;
		}

		return false;
	}

	private boolean isDirectAirportPlace(
			GooglePlaceDetailsResponse response
	) {
		if (response == null || response.result() == null) {
			return false;
		}

		GooglePlaceDetailsResponse.Result result = response.result();

		if (result.types() != null
				&& result.types().contains("airport")) {

			return true;
		}

		if (looksLikeStrictAirportText(result.name())) {
			return true;
		}

		return looksLikeStrictAirportText(
				result.formatted_address()
		);
	}

	private boolean isTerminalLikePlace(
			GooglePlaceDetailsResponse response
	) {
		if (response == null || response.result() == null) {
			return false;
		}

		GooglePlaceDetailsResponse.Result result = response.result();

		return looksLikeAirportTerminalText(result.name())
				|| looksLikeAirportTerminalText(
				result.formatted_address()
		);
	}

	private boolean isAirportNearby(
			double latitude,
			double longitude
	) {
		GoogleNearbySearchResponse response =
				googleMapsWebClient.get()
						.uri(uri -> uri
								.path("/place/nearbysearch/json")
								.queryParam(
										"location",
										latitude + "," + longitude
								)
								.queryParam(
										"radius",
										airportRadiusMeters
								)
								.queryParam(
										"type",
										"airport"
								)
								.queryParam(
										"key",
										apiKey
								)
								.build()
						)
						.retrieve()
						.bodyToMono(
								GoogleNearbySearchResponse.class
						)
						.block();

		return response != null
				&& response.results() != null
				&& !response.results().isEmpty();
	}

	private boolean looksLikeStrictAirportText(String value) {
		if (!hasText(value)) {
			return false;
		}

		String normalized = normalize(value);

		if (normalized.matches(
				".*\\bairport\\s+(road|flyover|area|colony|line|metro|station)\\b.*"
		)) {
			return false;
		}

		if (normalized.matches(
				".*\\b(international|domestic)\\s+airport\\b.*"
		)) {
			return true;
		}

		if (normalized.matches(
				".*\\bairport\\s+terminal\\b.*"
		)) {
			return true;
		}

		if (normalized.matches(
				".*\\bterminal\\s*[0-9a-z]?\\b.*"
		) && normalized.contains("airport")) {
			return true;
		}

		return normalized.matches(".*\\bairport\\b.*");
	}

	private boolean looksLikeAirportTerminalText(String value) {
		if (!hasText(value)) {
			return false;
		}

		String normalized = normalize(value);

		return normalized.matches(
				".*\\bterminal\\s*[0-9a-z]?\\b.*"
		)
				|| normalized.matches(
				".*\\bt\\s*-?\\s*[0-9]\\b.*"
		)
				|| normalized.matches(
				".*\\b(arrival|arrivals|departure|departures)\\s+terminal\\b.*"
		)
				|| normalized.matches(
				".*\\bterminal\\s+(arrival|arrivals|departure|departures)\\b.*"
		);
	}

	/*
	 * =========================================================
	 * DISTANCE AND TIME
	 * =========================================================
	 */

	@Override
	@Retry(name = "googleDistance")
	@CircuitBreaker(
			name = "googleDistance",
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

		String origins =
				source.getLatitude()
						+ ","
						+ source.getLongitude();

		String destinations =
				destination.getLatitude()
						+ ","
						+ destination.getLongitude();

		GoogleDistanceMatrixResponse response =
				googleMapsWebClient.get()
						.uri(uri -> uri
								.path("/distancematrix/json")
								.queryParam(
										"origins",
										origins
								)
								.queryParam(
										"destinations",
										destinations
								)
								.queryParam(
										"mode",
										"driving"
								)
								.queryParam(
										"key",
										apiKey
								)
								.build()
						)
						.retrieve()
						.bodyToMono(
								GoogleDistanceMatrixResponse.class
						)
						// Bounds the whole request/response wait -- a hung
						// Google Distance Matrix call must fail fast enough
						// for GeoProviderChain to fall through to Haversine
						// rather than hang indefinitely.
						.timeout(Duration.ofMillis(responseTimeoutMs))
						.block();

		validateDistanceMatrixResponse(
				response,
				origins,
				destinations
		);

		var element =
				response.rows()
						.get(0)
						.elements()
						.get(0);

		return new DistanceTimeResult(
				element.distance().value() / 1000.0,
				element.duration().value(),
				false,
				getName(),
				null // Distance Matrix API never returns route geometry
		);
	}

	private void validateDistanceMatrixResponse(
			GoogleDistanceMatrixResponse response,
			String origins,
			String destinations
	) {
		if (response == null) {
			throw new IllegalStateException(
					"Google Distance Matrix returned null response"
			);
		}

		if (!"OK".equalsIgnoreCase(response.status())) {
			throw new IllegalStateException(
					"Google Distance Matrix failed. status="
							+ response.status()
							+ ", error="
							+ response.error_message()
							+ ", origins="
							+ origins
							+ ", destinations="
							+ destinations
			);
		}

		if (response.rows() == null
				|| response.rows().isEmpty()) {

			throw new IllegalStateException(
					"Google Distance Matrix rows missing. origins="
							+ origins
							+ ", destinations="
							+ destinations
			);
		}

		if (response.rows().get(0).elements() == null
				|| response.rows()
				.get(0)
				.elements()
				.isEmpty()) {

			throw new IllegalStateException(
					"Google Distance Matrix elements missing. origins="
							+ origins
							+ ", destinations="
							+ destinations
			);
		}

		var element =
				response.rows()
						.get(0)
						.elements()
						.get(0);

		if (!"OK".equalsIgnoreCase(element.status())) {
			throw new IllegalStateException(
					"Google Distance Matrix element failed. elementStatus="
							+ element.status()
							+ ", origins="
							+ origins
							+ ", destinations="
							+ destinations
			);
		}

		if (element.distance() == null
				|| element.distance().value() == null) {

			throw new IllegalStateException(
					"Google Distance Matrix distance missing. origins="
							+ origins
							+ ", destinations="
							+ destinations
			);
		}

		if (element.duration() == null
				|| element.duration().value() == null) {

			throw new IllegalStateException(
					"Google Distance Matrix duration missing. origins="
							+ origins
							+ ", destinations="
							+ destinations
			);
		}
	}

	/*
	 * =========================================================
	 * STATE
	 * =========================================================
	 */

	@Override
	@Retry(name = "googleGeo")
	@CircuitBreaker(
			name = "googleGeo",
			fallbackMethod = "stateFallback"
	)
	public String resolveState(AddressSnapshot address) {
		if (address == null) {
			throw new IllegalArgumentException(
					"Address is required for state resolution"
			);
		}

		List<String> failedStrategies = new ArrayList<>();

		/*
		 * Strategy 1:
		 * Resolve through Google Place Details when a Place ID exists.
		 */
		if (hasText(address.getGooglePlaceId())) {
			try {
				GooglePlaceDetailsResponse response =
						fetchPlaceDetails(
								address.getGooglePlaceId(),
								"address_components"
						);

				return extractState(response);
			} catch (Exception ex) {
				failedStrategies.add("PLACE_DETAILS");

				logStateStrategyFailure(
						"PLACE_DETAILS",
						ex
				);
			}
		}

		/*
		 * Strategy 2:
		 * Reverse geocode the coordinates when valid coordinates exist.
		 */
		if (hasValidLatLng(address)) {
			try {
				GoogleGeocodingResponse response =
						fetchReverseGeocoding(
								address.getLatitude(),
								address.getLongitude()
						);

				return extractState(response);
			} catch (Exception ex) {
				failedStrategies.add("REVERSE_GEOCODING");

				logStateStrategyFailure(
						"REVERSE_GEOCODING",
						ex
				);
			}
		}

		/*
		 * Strategy 3:
		 * Geocode the formatted address.
		 */
		if (hasText(address.getFormattedAddress())) {
			try {
				GoogleGeocodingResponse response =
						fetchAddressGeocoding(
								address.getFormattedAddress()
						);

				return extractState(response);
			} catch (Exception ex) {
				failedStrategies.add("ADDRESS_GEOCODING");

				logStateStrategyFailure(
						"ADDRESS_GEOCODING",
						ex
				);
			}
		}

		if (failedStrategies.isEmpty()) {
			throw new IllegalStateException(
					"State resolution requires a place ID, valid coordinates, or formatted address"
			);
		}

		throw new IllegalStateException(
				"Google could not resolve state using: "
						+ String.join(
						", ",
						failedStrategies
				)
		);
	}

	/*
	 * =========================================================
	 * CITY
	 * =========================================================
	 */

	@Override
	@Retry(name = "googlePlaces")
	@CircuitBreaker(
			name = "googlePlaces",
			fallbackMethod = "cityFallback"
	)
	public String resolveCity(AddressSnapshot address) {

		if (address == null) {
			return null;
		}

		if (!hasText(address.getGooglePlaceId())) {
			return AddressParsingUtil.parseCity(address);
		}

		GooglePlaceDetailsResponse response =
				fetchPlaceDetails(
						address.getGooglePlaceId(),
						"address_components,formatted_address"
				);

		String city = extractCity(response);

		if (hasText(city)) {
			return city;
		}

		if (response != null && response.result() != null) {
			String parsedFromGoogleAddress =
					AddressParsingUtil.parseCity(
							response.result()
									.formatted_address()
					);

			if (hasText(parsedFromGoogleAddress)) {
				return parsedFromGoogleAddress;
			}
		}

		return AddressParsingUtil.parseCity(address);
	}

	/*
	 * =========================================================
	 * GOOGLE REQUESTS
	 * =========================================================
	 */

	private GooglePlaceDetailsResponse fetchPlaceDetails(
			String placeId,
			String fields
	) {
		if (!hasText(placeId)) {
			throw new IllegalArgumentException(
					"placeId is required"
			);
		}

		return googleMapsWebClient.get()
				.uri(uri -> uri
						.path("/place/details/json")
						.queryParam(
								"place_id",
								placeId
						)
						.queryParam(
								"fields",
								fields
						)
						.queryParam(
								"key",
								apiKey
						)
						.build()
				)
				.retrieve()
				.bodyToMono(
						GooglePlaceDetailsResponse.class
				)
				.block();
	}

	private GoogleGeocodingResponse fetchReverseGeocoding(
			double latitude,
			double longitude
	) {
		return googleMapsWebClient.get()
				.uri(uri -> uri
						.path("/geocode/json")
						.queryParam(
								"latlng",
								latitude + "," + longitude
						)
						.queryParam(
								"language",
								"en"
						)
						.queryParam(
								"key",
								apiKey
						)
						.build()
				)
				.retrieve()
				.bodyToMono(
						GoogleGeocodingResponse.class
				)
				.block();
	}

	private GoogleGeocodingResponse fetchAddressGeocoding(
			String formattedAddress
	) {
		return googleMapsWebClient.get()
				.uri(uri -> uri
						.path("/geocode/json")
						.queryParam(
								"address",
								formattedAddress
						)
						.queryParam(
								"language",
								"en"
						)
						.queryParam(
								"region",
								"in"
						)
						.queryParam(
								"key",
								apiKey
						)
						.build()
				)
				.retrieve()
				.bodyToMono(
						GoogleGeocodingResponse.class
				)
				.block();
	}

	/*
	 * =========================================================
	 * STATE EXTRACTION
	 * =========================================================
	 */

	private String extractState(
			GooglePlaceDetailsResponse response
	) {
		if (response == null) {
			throw new IllegalStateException(
					"Google Place Details returned null response"
			);
		}

		if (!"OK".equalsIgnoreCase(response.status())) {
			throw new IllegalStateException(
					"Google Place Details failed. status="
							+ response.status()
			);
		}

		if (response.result() == null
				|| response.result().address_components() == null) {

			throw new IllegalStateException(
					"Address components missing from Google Place Details response"
			);
		}

		return extractState(
				response.result().address_components()
		);
	}

	@SuppressWarnings("null")
	private String extractState(
			GoogleGeocodingResponse response
	) {
		if (response == null) {
			throw new IllegalStateException(
					"Google Geocoding returned null response"
			);
		}

		if (!"OK".equalsIgnoreCase(response.status())) {
			throw new IllegalStateException(
					"Google Geocoding failed. status="
							+ response.status()
							+ ", error="
							+ response.error_message()
			);
		}

		if (response.results() == null
				|| response.results().isEmpty()) {

			throw new IllegalStateException(
					"Google Geocoding returned no results"
			);
		}

		return response.results().stream()
				.filter(result ->
						result.address_components() != null
				)
				.flatMap(result ->
						result.address_components().stream()
				)
				.filter(component ->
						component.types() != null
								&& component.types().contains(
								"administrative_area_level_1"
						)
				)
				.map(
						GooglePlaceDetailsResponse.AddressComponent::long_name
				)
				.filter(this::hasText)
				.map(AddressParsingUtil::normalizeStateName)
				.findFirst()
				.orElseThrow(() ->
						new IllegalStateException(
								"State not found in Google Geocoding response"
						)
				);
	}

	@SuppressWarnings("null")
	private String extractState(
			List<GooglePlaceDetailsResponse.AddressComponent> addressComponents
	) {
		return addressComponents.stream()
				.filter(component ->
						component.types() != null
								&& component.types().contains(
								"administrative_area_level_1"
						)
				)
				.map(
						GooglePlaceDetailsResponse.AddressComponent::long_name
				)
				.filter(this::hasText)
				.map(AddressParsingUtil::normalizeStateName)
				.findFirst()
				.orElseThrow(() ->
						new IllegalStateException(
								"State not found in Google response"
						)
				);
	}

	/*
	 * =========================================================
	 * CITY EXTRACTION
	 * =========================================================
	 */

	private String extractCity(
			GooglePlaceDetailsResponse response
	) {
		if (response == null
				|| response.result() == null
				|| response.result().address_components() == null) {

			return null;
		}

		return firstAddressComponent(
				response,
				"locality"
		)
				.or(() ->
						firstAddressComponent(
								response,
								"postal_town"
						)
				)
				.or(() ->
						firstAddressComponent(
								response,
								"administrative_area_level_3"
						)
				)
				.or(() ->
						firstAddressComponent(
								response,
								"administrative_area_level_2"
						)
				)
				.or(() ->
						firstAddressComponent(
								response,
								"sublocality_level_1"
						)
				)
				.or(() ->
						firstAddressComponent(
								response,
								"sublocality"
						)
				)
				.orElse(null);
	}

	private java.util.Optional<String> firstAddressComponent(
			GooglePlaceDetailsResponse response,
			String type
	) {
		return response.result()
				.address_components()
				.stream()
				.filter(component ->
						component.types() != null
								&& component.types().contains(type)
				)
				.map(component ->
						component.long_name() == null
								? null
								: component.long_name().trim()
				)
				.filter(this::hasText)
				.findFirst();
	}

	/*
	 * =========================================================
	 * RESILIENCE FALLBACKS
	 * =========================================================
	 */

	private boolean isAirportFallback(
			AddressSnapshot address,
			Throwable ex
	) {
		log.warn(
				"Airport check failed in Google provider. formattedAddress={}, placeId={}",
				address != null
						? address.getFormattedAddress()
						: null,
				address != null
						? address.getGooglePlaceId()
						: null,
				ex
		);

		if (address == null) {
			return false;
		}

		return looksLikeStrictAirportText(
				address.getFormattedAddress()
		)
				|| looksLikeAirportTerminalText(
				address.getFormattedAddress()
		);
	}

	private DistanceTimeResult distanceFallback(
			AddressSnapshot source,
			AddressSnapshot destination,
			Throwable ex
	) {
		throw new RuntimeException(
				"Google Distance Matrix unavailable",
				ex
		);
	}

	private String stateFallback(
			AddressSnapshot address,
			Throwable ex
	) {
		log.warn(
				"Google state resolution failed after all strategies. placeId={}, reason={}",
				address != null
						? address.getGooglePlaceId()
						: null,
				ex.getMessage()
		);

		log.debug(
				"Google state resolution failure",
				ex
		);

		/*
		 * Throw so GeoProviderChain continues to FallbackGeoProvider.
		 */
		throw new IllegalStateException(
				"Unable to resolve state using Google",
				ex
		);
	}

	private String cityFallback(
			AddressSnapshot address,
			Throwable ex
	) {
		log.warn(
				"City resolution failed for placeId={}, using formattedAddress parser",
				address != null
						? address.getGooglePlaceId()
						: null,
				ex
		);

		return AddressParsingUtil.parseCity(address);
	}

	/*
	 * =========================================================
	 * HELPERS
	 * =========================================================
	 */

	private void logStateStrategyFailure(
			String strategy,
			Exception ex
	) {
		log.info(
				"Google state resolution strategy {} failed; trying the next strategy. reason={}",
				strategy,
				ex.getMessage()
		);

		log.debug(
				"Google state resolution strategy failure",
				ex
		);
	}

	private boolean hasLatLng(AddressSnapshot address) {
		return address != null
				&& address.getLatitude() != null
				&& address.getLongitude() != null;
	}

	private boolean hasValidLatLng(AddressSnapshot address) {
		if (!hasLatLng(address)) {
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

	private String normalize(String value) {
		if (value == null) {
			return "";
		}

		return value.trim()
				.replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT);
	}

	/*
	 * Kept inside this provider so that no additional response DTO file
	 * is required solely for state resolution.
	 */
	private record GoogleGeocodingResponse(
			String status,
			String error_message,
			List<GeocodingResult> results
	) {
	}

	private record GeocodingResult(
			List<GooglePlaceDetailsResponse.AddressComponent> address_components
	) {
	}
}
package com.core.location.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.core.location.api.GeoPoint;
import com.core.location.provider.dto.OrsDirectionsResponse.Feature;
import com.core.location.provider.dto.OrsDirectionsResponse.Geometry;
import com.core.location.provider.dto.OrsDirectionsResponse.Properties;
import com.core.location.provider.dto.OrsDirectionsResponse.Summary;
import com.core.models.embedded.AddressSnapshot;

class OpenRouteServiceGeoProviderTest {

	private final OpenRouteServiceGeoProvider provider =
			new OpenRouteServiceGeoProvider(WebClient.builder().build());

	@Test
	void reportsItsOwnName() {
		assertEquals("OPEN_ROUTE_SERVICE", provider.getName());
	}

	@Test
	void doesNotClaimToSupportAirportDetection() {
		assertThrows(
				UnsupportedOperationException.class,
				() -> provider.isAirport(null)
		);
	}

	@Test
	void doesNotClaimToSupportStateResolution() {
		assertThrows(
				UnsupportedOperationException.class,
				() -> provider.resolveState(null)
		);
	}

	@Test
	void doesNotClaimToSupportCityResolution() {
		assertThrows(
				UnsupportedOperationException.class,
				() -> provider.resolveCity(null)
		);
	}

	@Test
	void rejectsSourceOrDestinationWithoutCoordinates() {
		AddressSnapshot addressWithoutLatLng = new AddressSnapshot(
				"Some place", null, null, null
		);

		AddressSnapshot addressWithLatLng = new AddressSnapshot(
				"Garage", null, 12.9716, 77.5946
		);

		assertThrows(
				IllegalArgumentException.class,
				() -> provider.calculateDistanceAndTime(addressWithoutLatLng, addressWithLatLng)
		);
	}

	/*
	 * ORS's GeoJSON LineString coordinates are ordered [lon, lat] (per the GeoJSON
	 * spec) -- this is the one part of geometry extraction most likely to be gotten
	 * backwards, so it gets a direct test independent of any live HTTP call.
	 */
	@Test
	void extractsRouteGeometry_flippingOrsLonLatToLatLng() {
		Feature feature = new Feature(
				new Properties(new Summary(18100.0, 1380.0)),
				new Geometry("LineString", List.of(
						List.of(77.5946, 12.9716),
						List.of(77.6, 13.0)
				))
		);

		List<GeoPoint> geometry = provider.extractGeometry(feature);

		assertEquals(2, geometry.size());
		assertEquals(new GeoPoint(12.9716, 77.5946), geometry.get(0));
		assertEquals(new GeoPoint(13.0, 77.6), geometry.get(1));
	}

	@Test
	void extractGeometry_returnsNull_whenOrsOmitsGeometry() {
		Feature featureWithoutGeometry = new Feature(new Properties(new Summary(18100.0, 1380.0)), null);

		assertNull(provider.extractGeometry(featureWithoutGeometry));
	}

	@Test
	void extractGeometry_skipsMalformedCoordinatePairs() {
		Feature feature = new Feature(
				new Properties(new Summary(18100.0, 1380.0)),
				new Geometry("LineString", List.of(
						List.of(77.5946, 12.9716),
						List.of(77.6) // malformed: only one ordinate
				))
		);

		List<GeoPoint> geometry = provider.extractGeometry(feature);

		assertEquals(1, geometry.size());
		assertTrue(geometry.contains(new GeoPoint(12.9716, 77.5946)));
	}
}

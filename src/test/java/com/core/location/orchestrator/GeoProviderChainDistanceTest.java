package com.core.location.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.core.location.api.DistanceTimeResult;
import com.core.location.api.GeoPoint;
import com.core.location.provider.GeoProvider;
import com.core.models.embedded.AddressSnapshot;

/*
 * Covers the fallback chain that the Drop->Garage (C->A) return-route estimate at
 * duty end relies on: ORS first (with real road geometry), Google second (distance/
 * duration only, no geometry), Haversine last (straight-line, no geometry). The
 * chain itself must not fabricate or drop geometry -- whatever the winning provider
 * returns is passed through unchanged.
 */
class GeoProviderChainDistanceTest {

	private final AddressSnapshot drop = new AddressSnapshot("Drop", null, 12.9716, 77.5946);
	private final AddressSnapshot garage = new AddressSnapshot("Garage", null, 13.05, 77.62);

	@Test
	void usesOrsResult_includingRouteGeometry_whenOrsSucceeds() {
		List<GeoPoint> orsGeometry = List.of(new GeoPoint(12.9716, 77.5946), new GeoPoint(13.05, 77.62));
		DistanceTimeResult orsResult = new DistanceTimeResult(18.1, 1380, false, "OPEN_ROUTE_SERVICE", orsGeometry);

		GeoProvider ors = mock(GeoProvider.class);
		when(ors.calculateDistanceAndTime(drop, garage)).thenReturn(orsResult);

		GeoProvider google = mock(GeoProvider.class);

		GeoProviderChain chain = new GeoProviderChain(List.of(ors, google));

		DistanceTimeResult result = chain.calculateDistance(drop, garage);

		assertEquals(18.1, result.distanceKm());
		assertEquals(1380, result.durationSeconds());
		assertEquals("OPEN_ROUTE_SERVICE", result.provider());
		assertEquals(orsGeometry, result.routeGeometry());
		assertTrue(result.routeGeometry() != null && !result.routeGeometry().isEmpty());
	}

	@Test
	void fallsBackToGoogle_withNoGeometry_whenOrsFails() {
		GeoProvider ors = mock(GeoProvider.class);
		when(ors.calculateDistanceAndTime(drop, garage)).thenThrow(new RuntimeException("ORS unavailable"));

		DistanceTimeResult googleResult = new DistanceTimeResult(18.4, 1400, false, "GOOGLE_MAPS", null);
		GeoProvider google = mock(GeoProvider.class);
		when(google.calculateDistanceAndTime(drop, garage)).thenReturn(googleResult);

		GeoProviderChain chain = new GeoProviderChain(List.of(ors, google));

		DistanceTimeResult result = chain.calculateDistance(drop, garage);

		assertEquals("GOOGLE_MAPS", result.provider());
		assertEquals(18.4, result.distanceKm());
		assertNull(result.routeGeometry());
	}

	@Test
	void fallsBackToHaversine_withNoGeometry_whenOrsAndGoogleBothFail() {
		GeoProvider ors = mock(GeoProvider.class);
		when(ors.calculateDistanceAndTime(drop, garage)).thenThrow(new RuntimeException("ORS unavailable"));

		GeoProvider google = mock(GeoProvider.class);
		when(google.calculateDistanceAndTime(drop, garage)).thenThrow(new RuntimeException("Google unavailable"));

		DistanceTimeResult haversineResult = new DistanceTimeResult(17.9, 1611, true, "HAVERSINE", null);
		GeoProvider haversine = mock(GeoProvider.class);
		when(haversine.calculateDistanceAndTime(drop, garage)).thenReturn(haversineResult);

		GeoProviderChain chain = new GeoProviderChain(List.of(ors, google, haversine));

		DistanceTimeResult result = chain.calculateDistance(drop, garage);

		assertEquals("HAVERSINE", result.provider());
		assertNull(result.routeGeometry());
	}

	@Test
	void throws_whenEveryProviderFails() {
		GeoProvider ors = mock(GeoProvider.class);
		when(ors.calculateDistanceAndTime(drop, garage)).thenThrow(new RuntimeException("ORS unavailable"));

		GeoProviderChain chain = new GeoProviderChain(List.of(ors));

		assertThrows(IllegalStateException.class, () -> chain.calculateDistance(drop, garage));
	}
}

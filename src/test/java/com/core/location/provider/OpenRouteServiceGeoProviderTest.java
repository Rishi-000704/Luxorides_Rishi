package com.core.location.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

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
}

package com.core.location.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.core.location.provider.GeoProvider;
import com.core.models.embedded.AddressSnapshot;

/*
 * Phase B -- GeoProviderChain.resolveState/resolveCity/isAirport now cache
 * through BoundedTtlCache instead of a plain unbounded ConcurrentHashMap
 * (see GeoProviderChain's own comment for the full rationale). These prove
 * the observable behavior through the real public API: a repeated lookup
 * for the same address never calls the underlying provider twice, and
 * genuinely different addresses are never conflated -- using whatever key
 * material each cache actually derives from (stateCacheKey/airportCacheKey
 * consider place id, then lat/lng, then formatted address; cityCacheKey
 * deliberately does not consider lat/lng at all -- place id, then
 * formatted address only -- verified by reading GeoProviderChain itself
 * rather than assumed).
 *
 * argThat lambdas below are null-safe (`a != null && ...`) because Mockito
 * can probe a registered matcher with a null argument internally when
 * multiple stubbings exist on the same method; a matcher that doesn't
 * guard against that throws a NullPointerException that has nothing to do
 * with the behavior under test.
 */
class GeoProviderChainCacheTest {

	private AddressSnapshot addressWithPlaceId(String placeId) {
		AddressSnapshot address = new AddressSnapshot();
		address.setGooglePlaceId(placeId);
		address.setFormattedAddress("123 Test Street");
		return address;
	}

	private AddressSnapshot addressWithFormattedAddress(String formattedAddress) {
		AddressSnapshot address = new AddressSnapshot();
		address.setFormattedAddress(formattedAddress);
		return address;
	}

	@Test
	void resolveState_repeatedLookup_sameAddress_hitsProviderOnlyOnce() {
		GeoProvider provider = mock(GeoProvider.class);
		when(provider.getName()).thenReturn("test-provider");
		when(provider.resolveState(any())).thenReturn("KA");
		GeoProviderChain chain = new GeoProviderChain(List.of(provider));

		AddressSnapshot address = addressWithPlaceId("place-1");

		assertEquals("KA", chain.resolveState(address));
		assertEquals("KA", chain.resolveState(address));
		assertEquals("KA", chain.resolveState(address));

		verify(provider, times(1)).resolveState(address);
	}

	@Test
	void resolveState_differentPlaceIds_areNeverConflated() {
		GeoProvider provider = mock(GeoProvider.class);
		when(provider.getName()).thenReturn("test-provider");
		when(provider.resolveState(argThat(a -> a != null && "place-1".equals(a.getGooglePlaceId()))))
				.thenReturn("KA");
		when(provider.resolveState(argThat(a -> a != null && "place-2".equals(a.getGooglePlaceId()))))
				.thenReturn("MH");
		GeoProviderChain chain = new GeoProviderChain(List.of(provider));

		assertEquals("KA", chain.resolveState(addressWithPlaceId("place-1")));
		assertEquals("MH", chain.resolveState(addressWithPlaceId("place-2")));
		// Re-resolve place-1 after place-2 -- must still be place-1's own cached value.
		assertEquals("KA", chain.resolveState(addressWithPlaceId("place-1")));

		verify(provider, times(1)).resolveState(argThat(a -> a != null && "place-1".equals(a.getGooglePlaceId())));
		verify(provider, times(1)).resolveState(argThat(a -> a != null && "place-2".equals(a.getGooglePlaceId())));
	}

	@Test
	void resolveState_differentCoordinateBuckets_areNeverConflated() {
		GeoProvider provider = mock(GeoProvider.class);
		when(provider.getName()).thenReturn("test-provider");
		when(provider.resolveState(any())).thenReturn("KA");
		GeoProviderChain chain = new GeoProviderChain(List.of(provider));

		AddressSnapshot bengaluru = new AddressSnapshot();
		bengaluru.setLatitude(12.97160);
		bengaluru.setLongitude(77.59460);

		AddressSnapshot chennai = new AddressSnapshot();
		chennai.setLatitude(13.08270);
		chennai.setLongitude(80.27070);

		// stateCacheKey (unlike cityCacheKey) does fall back to lat/lng when
		// there is no place id -- these differ well past the 5-decimal-place
		// rounding the cache key uses.
		chain.resolveState(bengaluru);
		chain.resolveState(chennai);

		verify(provider, times(2)).resolveState(any());
	}

	@Test
	void resolveCity_differentPlaceIds_areNeverConflated() {
		GeoProvider provider = mock(GeoProvider.class);
		when(provider.getName()).thenReturn("test-provider");
		when(provider.resolveCity(argThat(a -> a != null && "place-1".equals(a.getGooglePlaceId()))))
				.thenReturn("Bengaluru");
		when(provider.resolveCity(argThat(a -> a != null && "place-2".equals(a.getGooglePlaceId()))))
				.thenReturn("Chennai");
		GeoProviderChain chain = new GeoProviderChain(List.of(provider));

		assertEquals("Bengaluru", chain.resolveCity(addressWithPlaceId("place-1")));
		assertEquals("Chennai", chain.resolveCity(addressWithPlaceId("place-2")));
		assertEquals("Bengaluru", chain.resolveCity(addressWithPlaceId("place-1")));

		verify(provider, times(1)).resolveCity(argThat(a -> a != null && "place-1".equals(a.getGooglePlaceId())));
		verify(provider, times(1)).resolveCity(argThat(a -> a != null && "place-2".equals(a.getGooglePlaceId())));
	}

	@Test
	void resolveCity_noPlaceId_fallsBackToFormattedAddress_differentAddressesAreNeverConflated() {
		// cityCacheKey (verified by reading GeoProviderChain) does not
		// consider lat/lng at all -- only place id, then formatted address.
		GeoProvider provider = mock(GeoProvider.class);
		when(provider.getName()).thenReturn("test-provider");
		when(provider.resolveCity(any())).thenReturn("Some City");
		GeoProviderChain chain = new GeoProviderChain(List.of(provider));

		chain.resolveCity(addressWithFormattedAddress("1 MG Road, Bengaluru"));
		chain.resolveCity(addressWithFormattedAddress("1 Anna Salai, Chennai"));
		// Repeat the first -- must be a cache hit, not a third provider call.
		chain.resolveCity(addressWithFormattedAddress("1 MG Road, Bengaluru"));

		verify(provider, times(2)).resolveCity(any());
	}

	@Test
	void isAirport_repeatedLookup_sameAddress_hitsProviderOnlyOnce() {
		GeoProvider provider = mock(GeoProvider.class);
		when(provider.getName()).thenReturn("test-provider");
		when(provider.isAirport(any())).thenReturn(true);
		GeoProviderChain chain = new GeoProviderChain(List.of(provider));

		AddressSnapshot address = addressWithPlaceId("airport-place-1");

		assertTrue(chain.isAirport(address));
		assertTrue(chain.isAirport(address));

		verify(provider, times(1)).isAirport(address);
	}
}

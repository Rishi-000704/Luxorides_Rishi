package com.core.location.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.location.api.DistanceTimeResult;
import com.core.location.api.GeoPoint;
import com.core.models.RouteCacheEntry;
import com.core.models.embedded.AddressSnapshot;
import com.core.repositories.RouteCacheEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * Covers the routing audit's Phase 2B/2C reuse behavior: fresh-hit avoids the
 * provider entirely, miss computes-and-persists, an estimated (haversine)
 * result is never persisted, stale entries are recomputed (never treated as
 * authoritative), a total live failure degrades to a stale cached result
 * when one exists, and concurrent identical requests on one instance are
 * coalesced into a single live call. GeoProviderChain itself is not touched
 * or re-tested here -- the Supplier<DistanceTimeResult> passed in stands in
 * for "whatever GeoProviderChain.calculateDistance would have returned".
 */
class RouteCacheServiceTest {

	private RouteCacheEntryRepository repository;
	private RouteCacheService service;

	private final AddressSnapshot source = new AddressSnapshot("Source", null, 12.97160, 77.59460);
	private final AddressSnapshot destination = new AddressSnapshot("Destination", null, 13.05000, 77.62000);

	@BeforeEach
	void setUp() {
		repository = mock(RouteCacheEntryRepository.class);
		service = new RouteCacheService(repository, new ObjectMapper());
	}

	private DistanceTimeResult realResult() {
		return new DistanceTimeResult(18.1, 1380, false, "OPEN_ROUTE_SERVICE",
				List.of(new GeoPoint(12.9716, 77.5946), new GeoPoint(13.05, 77.62)));
	}

	private RouteCacheEntry freshEntry() throws Exception {
		RouteCacheEntry entry = new RouteCacheEntry();
		entry.setId("route-1");
		entry.setOriginKey("12.9716,77.5946");
		entry.setDestinationKey("13.05,77.62");
		entry.setProfile("driving-car");
		entry.setDistanceKm(18.1);
		entry.setDurationSeconds(1380L);
		entry.setProvider("OPEN_ROUTE_SERVICE");
		entry.setRouteGeometryJson(new ObjectMapper().writeValueAsString(realResult().routeGeometry()));
		entry.setCalculatedAt(Instant.now());
		entry.setUsageCount(0L);
		entry.setLastUsedAt(Instant.now());
		return entry;
	}

	@Test
	void cacheMiss_computesLive_andPersistsRealResult() {
		when(repository.findByOriginKeyAndDestinationKeyAndProfile(anyString(), anyString(), eq("driving-car")))
				.thenReturn(Optional.empty());

		DistanceTimeResult live = realResult();
		Supplier<DistanceTimeResult> supplier = mockSupplier(live);

		DistanceTimeResult result = service.getOrCompute(source, destination, supplier);

		assertEquals(live, result);
		verify(supplier, times(1)).get();
		verify(repository, times(1)).save(any(RouteCacheEntry.class));
	}

	@Test
	void cacheHit_returnsCachedResult_withoutCallingSupplier() throws Exception {
		RouteCacheEntry entry = freshEntry();
		when(repository.findByOriginKeyAndDestinationKeyAndProfile("12.9716,77.5946", "13.05,77.62", "driving-car"))
				.thenReturn(Optional.of(entry));

		Supplier<DistanceTimeResult> supplier = mockSupplier(realResult());

		DistanceTimeResult result = service.getOrCompute(source, destination, supplier);

		assertEquals(18.1, result.distanceKm());
		assertEquals(1380L, result.durationSeconds());
		assertEquals("OPEN_ROUTE_SERVICE", result.provider());
		verify(supplier, never()).get();
		verify(repository, times(1)).recordHit(eq("route-1"), any(Instant.class));
	}

	@Test
	void staleCache_isNotReused_recomputesLiveInstead() throws Exception {
		RouteCacheEntry stale = freshEntry();
		stale.setCalculatedAt(Instant.now().minus(25, ChronoUnit.HOURS)); // past the 24h TTL

		when(repository.findByOriginKeyAndDestinationKeyAndProfile("12.9716,77.5946", "13.05,77.62", "driving-car"))
				.thenReturn(Optional.of(stale));

		DistanceTimeResult fresherLive = new DistanceTimeResult(19.0, 1500, false, "OPEN_ROUTE_SERVICE", null);
		Supplier<DistanceTimeResult> supplier = mockSupplier(fresherLive);

		DistanceTimeResult result = service.getOrCompute(source, destination, supplier);

		assertEquals(19.0, result.distanceKm());
		verify(supplier, times(1)).get();
		verify(repository, times(1)).save(any(RouteCacheEntry.class));
	}

	@Test
	void estimatedResult_isNeverPersisted() {
		when(repository.findByOriginKeyAndDestinationKeyAndProfile(anyString(), anyString(), eq("driving-car")))
				.thenReturn(Optional.empty());

		DistanceTimeResult estimate = new DistanceTimeResult(17.9, 1611, true, "HAVERSINE", null);
		Supplier<DistanceTimeResult> supplier = mockSupplier(estimate);

		DistanceTimeResult result = service.getOrCompute(source, destination, supplier);

		assertEquals("HAVERSINE", result.provider());
		verify(repository, never()).save(any(RouteCacheEntry.class));
	}

	@Test
	void liveFailure_fallsBackToStaleCachedResult_whenOneExists() throws Exception {
		RouteCacheEntry stale = freshEntry();
		stale.setCalculatedAt(Instant.now().minus(48, ChronoUnit.HOURS));

		when(repository.findByOriginKeyAndDestinationKeyAndProfile("12.9716,77.5946", "13.05,77.62", "driving-car"))
				.thenReturn(Optional.of(stale));

		Supplier<DistanceTimeResult> failingSupplier = () -> {
			throw new IllegalStateException("All geo providers failed");
		};

		DistanceTimeResult result = service.getOrCompute(source, destination, failingSupplier);

		assertEquals(18.1, result.distanceKm());
		assertEquals("OPEN_ROUTE_SERVICE", result.provider());
	}

	@Test
	void liveFailure_propagates_whenNoCachedResultExistsAtAll() {
		when(repository.findByOriginKeyAndDestinationKeyAndProfile(anyString(), anyString(), eq("driving-car")))
				.thenReturn(Optional.empty());

		Supplier<DistanceTimeResult> failingSupplier = () -> {
			throw new IllegalStateException("All geo providers failed");
		};

		assertThrows(IllegalStateException.class, () -> service.getOrCompute(source, destination, failingSupplier));
	}

	@Test
	void missingCoordinates_bypassesCacheEntirely() {
		AddressSnapshot noCoords = new AddressSnapshot("No coords", null, null, null);
		Supplier<DistanceTimeResult> supplier = mockSupplier(realResult());

		service.getOrCompute(noCoords, destination, supplier);

		verify(supplier, times(1)).get();
		verify(repository, never()).findByOriginKeyAndDestinationKeyAndProfile(anyString(), anyString(), anyString());
	}

	@Test
	void routeCacheReadFailure_degradesToLiveComputation() {
		when(repository.findByOriginKeyAndDestinationKeyAndProfile(anyString(), anyString(), eq("driving-car")))
				.thenThrow(new RuntimeException("DB unavailable"));

		Supplier<DistanceTimeResult> supplier = mockSupplier(realResult());

		DistanceTimeResult result = service.getOrCompute(source, destination, supplier);

		assertEquals("OPEN_ROUTE_SERVICE", result.provider());
		verify(supplier, times(1)).get();
	}

	@Test
	void concurrentIdenticalRequests_areCoalesced_intoOneLiveCall() throws Exception {
		when(repository.findByOriginKeyAndDestinationKeyAndProfile(anyString(), anyString(), eq("driving-car")))
				.thenReturn(Optional.empty());

		AtomicInteger callCount = new AtomicInteger(0);
		CountDownLatch releaseLatch = new CountDownLatch(1);

		Supplier<DistanceTimeResult> slowSupplier = () -> {
			callCount.incrementAndGet();
			try {
				releaseLatch.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return realResult();
		};

		int threadCount = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		try {
			List<Future<DistanceTimeResult>> futures = new java.util.ArrayList<>();
			for (int i = 0; i < threadCount; i++) {
				futures.add(pool.submit(() -> service.getOrCompute(source, destination, slowSupplier)));
			}

			Thread.sleep(200); // let every thread reach the coalescing point
			releaseLatch.countDown();

			for (Future<DistanceTimeResult> future : futures) {
				assertEquals(18.1, future.get().distanceKm());
			}
		} finally {
			pool.shutdownNow();
		}

		assertEquals(1, callCount.get(), "only one thread should have actually invoked the live supplier");
	}

	/*
	 * A GPS fix a few centimeters off the exact coordinates the route was
	 * originally computed for must still resolve to the same cache row --
	 * both AddressSnapshots below round to the identical "12.9716,77.5946"
	 * key at 5-decimal-place (~1.1m) precision.
	 */
	@Test
	void cacheKey_roundsCoordinates_soTinyGpsJitterStillHits() throws Exception {
		AddressSnapshot jitteredSource = new AddressSnapshot("Source jitter", null, 12.971601, 77.594599);

		when(repository.findByOriginKeyAndDestinationKeyAndProfile("12.9716,77.5946", "13.05,77.62", "driving-car"))
				.thenReturn(Optional.of(freshEntry()));

		Supplier<DistanceTimeResult> supplier = mockSupplier(realResult());

		DistanceTimeResult exact = service.getOrCompute(source, destination, supplier);
		DistanceTimeResult jittered = service.getOrCompute(jitteredSource, destination, supplier);

		assertEquals(exact.distanceKm(), jittered.distanceKm());
		verify(supplier, never()).get();
	}

	@SuppressWarnings("unchecked")
	private Supplier<DistanceTimeResult> mockSupplier(DistanceTimeResult result) {
		Supplier<DistanceTimeResult> supplier = mock(Supplier.class);
		when(supplier.get()).thenReturn(result);
		return supplier;
	}
}

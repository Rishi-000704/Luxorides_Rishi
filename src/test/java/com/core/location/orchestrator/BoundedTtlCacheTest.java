package com.core.location.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/*
 * Phase B -- BoundedTtlCache backs GeoProviderChain's state/city/airport
 * caches (see GeoProviderChain's own comment for why: unbounded
 * ConcurrentHashMaps before this, no eviction, no expiry). These prove the
 * three properties the task requires directly: a valid entry is reused
 * (cache hit), an expired entry triggers a fresh load, and capacity being
 * exceeded evicts the least-recently-used entry, keeping size bounded --
 * plus the two behavior-preservation edges (null loader result isn't
 * cached; a throwing loader leaves the cache unchanged) that fall out of
 * relying directly on ConcurrentHashMap.compute()'s own documented
 * contract. The fake clock (package-private constructor) makes TTL
 * expiry deterministic -- no real sleep anywhere in this file.
 */
class BoundedTtlCacheTest {

	@Test
	void cacheHit_reusesTheValue_withoutCallingTheLoaderAgain() {
		AtomicInteger loads = new AtomicInteger();
		BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(10, Duration.ofMinutes(1));

		String first = cache.computeIfAbsent("k1", k -> {
			loads.incrementAndGet();
			return "state-for-" + k;
		});
		String second = cache.computeIfAbsent("k1", k -> {
			loads.incrementAndGet();
			return "state-for-" + k;
		});

		assertEquals("state-for-k1", first);
		assertEquals("state-for-k1", second);
		assertEquals(1, loads.get(), "second call must be a cache hit, not a second load");
	}

	@Test
	void expiredEntry_triggersAFreshLoad_insteadOfReturningStaleData() {
		AtomicLong fakeNow = new AtomicLong(0);
		AtomicInteger loads = new AtomicInteger();
		BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(10, Duration.ofSeconds(1), fakeNow::get);

		String first = cache.computeIfAbsent("k1", k -> {
			loads.incrementAndGet();
			return "value-" + loads.get();
		});

		// Advance the fake clock well past the 1-second TTL.
		fakeNow.addAndGet(Duration.ofSeconds(5).toNanos());

		String second = cache.computeIfAbsent("k1", k -> {
			loads.incrementAndGet();
			return "value-" + loads.get();
		});

		assertEquals("value-1", first);
		assertEquals("value-2", second, "an expired entry must never be returned -- it must reload");
		assertEquals(2, loads.get());
	}

	@Test
	void capacityExceeded_evictsTheLeastRecentlyUsedEntry_cacheStaysBounded() {
		BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(2, Duration.ofMinutes(1));

		cache.computeIfAbsent("k1", k -> "v1");
		cache.computeIfAbsent("k2", k -> "v2");
		// Touch k1 again so k2 becomes the least-recently-used of the two.
		cache.computeIfAbsent("k1", k -> "should-not-reload");
		// A third distinct key exceeds the size-2 cap -- k2 (LRU) must be evicted, not k1.
		cache.computeIfAbsent("k3", k -> "v3");

		assertEquals(2, cache.size(), "cache must never grow past its configured maximum size");

		// Check k1 first: it was touched more recently than k2 above, so it
		// must still be cached. Reading it here also re-touches it, making
		// it the most-recently-used entry going into the next step.
		AtomicInteger k1Reloads = new AtomicInteger();
		cache.computeIfAbsent("k1", k -> {
			k1Reloads.incrementAndGet();
			return "should-not-happen";
		});
		assertEquals(0, k1Reloads.get(), "k1 was touched more recently than k2 and must still be cached");

		// Confirm k2 was the one actually evicted. This insertion itself
		// exceeds capacity again and evicts k3 (now the LRU after the k1
		// read above) -- deliberately not asserted further, since this test
		// is only about k1 vs. k2.
		AtomicInteger k2Reloads = new AtomicInteger();
		cache.computeIfAbsent("k2", k -> {
			k2Reloads.incrementAndGet();
			return "v2-reloaded";
		});
		assertEquals(1, k2Reloads.get(), "k2 must have been evicted (it was the LRU entry) and required a reload");
	}

	@Test
	void nullLoaderResult_isNeverCached_everyCallRetries() {
		AtomicInteger loads = new AtomicInteger();
		BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(10, Duration.ofMinutes(1));

		String first = cache.computeIfAbsent("unresolvable", k -> {
			loads.incrementAndGet();
			return null;
		});
		String second = cache.computeIfAbsent("unresolvable", k -> {
			loads.incrementAndGet();
			return null;
		});

		assertNull(first);
		assertNull(second);
		assertEquals(2, loads.get(), "a null result must never be cached -- matches ConcurrentHashMap.computeIfAbsent");
		assertEquals(0, cache.size());
	}

	@Test
	void throwingLoader_leavesTheCacheUnchanged_exceptionPropagates() {
		BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(10, Duration.ofMinutes(1));

		assertThrows(IllegalStateException.class, () -> cache.computeIfAbsent("k1", k -> {
			throw new IllegalStateException("all providers failed");
		}));

		assertEquals(0, cache.size(), "a failed load must not leave a phantom cache entry");
	}
}

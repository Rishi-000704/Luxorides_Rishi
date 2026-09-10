package com.core.location.orchestrator;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/*
 * Phase B -- minimal bounded LRU + TTL cache, JDK-only. No cache library
 * (Guava/Caffeine) is on this project's classpath, direct or transitive
 * (verified), and this need doesn't justify adding one.
 *
 * Two structures, not one, deliberately:
 *  - `data` (ConcurrentHashMap) holds the actual values and does the real
 *    work via compute(), which is atomic PER KEY -- concurrent callers for
 *    the *same* key block on that key only until the first one's loader
 *    (a network call to a geo provider) finishes, exactly like the
 *    ConcurrentHashMap.computeIfAbsent this replaces already did; callers
 *    for *different* keys are never blocked by this. There is no map-wide
 *    lock, so unrelated geographic lookups never serialize against each
 *    other or against an in-flight provider call.
 *  - `recency` (a synchronized access-order LinkedHashMap, used only to
 *    decide what to evict) tracks which key was touched most recently.
 *    Its lock is only ever held for a cheap bookkeeping put/evict, never
 *    across a loader call, so it cannot itself become a bottleneck.
 */
final class BoundedTtlCache<K, V> {

	private record Entry<V>(V value, long expiresAtNanos) {
	}

	private final ConcurrentHashMap<K, Entry<V>> data = new ConcurrentHashMap<>();
	private final Map<K, Boolean> recency;
	private final long ttlNanos;
	private final LongSupplier nanoTimeSource;

	BoundedTtlCache(int maxSize, Duration ttl) {
		this(maxSize, ttl, System::nanoTime);
	}

	// Package-private: lets tests inject a fake clock instead of relying on
	// a real (however short) sleep to observe TTL expiry deterministically.
	// Production always goes through the public constructor above.
	BoundedTtlCache(int maxSize, Duration ttl, LongSupplier nanoTimeSource) {
		this.ttlNanos = ttl.toNanos();
		this.nanoTimeSource = nanoTimeSource;
		this.recency = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, Boolean> eldest) {
				boolean evict = size() > maxSize;
				if (evict) {
					data.remove(eldest.getKey());
				}
				return evict;
			}
		});
	}

	V computeIfAbsent(K key, Function<K, V> loader) {
		Entry<V> existing = data.get(key);
		if (existing != null && notExpired(existing)) {
			touch(key);
			return existing.value();
		}

		// Matches ConcurrentHashMap.compute()'s own contract, which this
		// relies on directly: if the loader throws, the exception
		// propagates and the map is left unchanged (a stale-but-not-yet-
		// evicted entry, if any, stays exactly as it was -- never served,
		// since the expiry check above always re-attempts it); if the
		// loader returns null, nothing is cached, matching
		// ConcurrentHashMap.computeIfAbsent's existing null handling
		// (resolveCity can legitimately return null for an unresolvable
		// address, and that must keep retrying on every call, not get
		// cached as a negative result for the TTL window).
		Entry<V> computed = data.compute(key, (k, current) -> {
			if (current != null && notExpired(current)) {
				return current;
			}
			V freshValue = loader.apply(k);
			return freshValue == null ? null : new Entry<>(freshValue, nanoTimeSource.getAsLong() + ttlNanos);
		});

		if (computed == null) {
			return null;
		}

		touch(key);
		return computed.value();
	}

	int size() {
		return data.size();
	}

	private boolean notExpired(Entry<V> entry) {
		return nanoTimeSource.getAsLong() < entry.expiresAtNanos();
	}

	private void touch(K key) {
		recency.put(key, Boolean.TRUE);
	}
}

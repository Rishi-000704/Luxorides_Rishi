package com.core.models;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Cost-aware route reuse (see routing audit, Phase 2B): a persistent,
 * multi-instance-shared cache of real, provider-computed distance/duration/
 * geometry results, keyed by rounded origin/destination coordinates + travel
 * profile. Deliberately DB-backed rather than the in-process
 * ConcurrentHashMap GeoProviderChain already uses for its lighter state/city/
 * airport lookups: distance/directions calls are this system's most
 * expensive and most frequently *repeated* external calls (see
 * MasterVehicleService.resolveEffectiveDutyType, recomputed on every
 * validateVehicle click for an itinerary already priced during
 * searchByItinerary; and ExternalDriverDutyService.getRouteForLeg, refetched
 * on every screen mount for waypoints fixed for the life of the duty leg),
 * and an in-process-only cache would not help a second application instance
 * see a route the first instance already paid to compute.
 *
 * One row per distinct (originKey, destinationKey, profile) -- a cache-miss
 * write overwrites the existing row for that key rather than inserting a new
 * one, so the table's size is bounded by the number of distinct real-world
 * route pairs ever requested, not by request volume.
 *
 * Only ever populated with genuine provider results (RouteCacheService never
 * persists a FallbackGeoProvider/haversine estimate): caching a low-quality
 * straight-line guess as if it were a real route would actively harm
 * correctness for as long as its TTL lasted.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "route_cache_entry", uniqueConstraints = {
		@UniqueConstraint(name = "uk_route_cache_key", columnNames = { "origin_key", "destination_key", "profile" })
}, indexes = {
		@Index(name = "idx_route_cache_calculated_at", columnList = "calculated_at")
})
public class RouteCacheEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;

	// "lat,lng" rounded to 5 decimal places (~1.1m) -- same precision
	// GeoProviderChain already uses for its own state/city/airport cache keys.
	@Column(name = "origin_key", nullable = false, length = 60)
	private String originKey;

	@Column(name = "destination_key", nullable = false, length = 60)
	private String destinationKey;

	// Always "driving-car" today (the only profile GeoProvider implementations
	// request) -- stored explicitly rather than assumed, so a future
	// alternate profile (e.g. two-wheeler) cannot silently collide with a
	// car route for the same coordinates.
	@Column(nullable = false, length = 30)
	private String profile;

	@Column(nullable = false)
	private Double distanceKm;

	@Column(nullable = false)
	private Long durationSeconds;

	// Which GeoProvider actually computed this (e.g. OPEN_ROUTE_SERVICE,
	// GOOGLE_MAPS) -- never HAVERSINE, see class comment.
	@Column(nullable = false, length = 30)
	private String provider;

	// JSON-serialized List<GeoPoint>, nullable (Google Distance Matrix results
	// never include geometry).
	@Lob
	@Column(columnDefinition = "TEXT")
	private String routeGeometryJson;

	// When this row's route was actually (re)computed from a live provider --
	// TTL freshness is checked against this, and it is deliberately NEVER
	// refreshed on a cache hit (see RouteCacheService): a popular route must
	// still expire on schedule, not be kept artificially "fresh" forever by
	// its own popularity.
	@Column(name = "calculated_at", nullable = false)
	private Instant calculatedAt;

	@Column(nullable = false)
	private Instant lastUsedAt;

	@Column(nullable = false)
	private Long usageCount;
}

package com.core.dtos.driverduty;

import java.util.List;

import com.core.location.api.GeoPoint;

/**
 * The independently-calculated Drop (C) -> Garage (A) return leg, as returned by
 * {@link com.core.location.orchestrator.GeoProviderChain} at duty end. This is the
 * single source of truth for both the fare's C->A component and the C->A route drawn
 * on the Driver App map -- the two must never diverge.
 *
 * {@code geometry} is only populated when a real road route came back (OpenRouteService
 * today; Google's Distance Matrix and the Haversine fallback have no route geometry).
 * When {@code routeAvailable} is false, the frontend must show an honest
 * "road route unavailable" state instead of fabricating a polyline.
 */
public record ReturnRouteEstimate(
        double distanceKm,
        long durationSeconds,
        String provider,
        boolean routeAvailable,
        GeoPoint dropLocation,
        GeoPoint garageLocation,
        List<GeoPoint> geometry
) {}

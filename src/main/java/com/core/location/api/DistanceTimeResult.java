package com.core.location.api;

import java.util.List;

public record DistanceTimeResult(
        double distanceKm,
        long durationSeconds,
        boolean estimated,
        String provider,
        List<GeoPoint> routeGeometry
) {}

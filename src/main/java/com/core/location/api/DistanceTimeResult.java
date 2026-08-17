package com.core.location.api;

public record DistanceTimeResult(
        double distanceKm,
        long durationSeconds,
        boolean estimated
) {}

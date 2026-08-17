package com.core.location.provider.dto;

import java.util.List;

public record GoogleNearbySearchResponse(
        List<Result> results
) {
    public record Result(
            Geometry geometry
    ) {}

    public record Geometry(
            Location location
    ) {}

    public record Location(
            double lat,
            double lng
    ) {}
}

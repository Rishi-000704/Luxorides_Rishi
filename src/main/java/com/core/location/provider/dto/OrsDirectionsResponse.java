package com.core.location.provider.dto;

import java.util.List;

public record OrsDirectionsResponse(
		List<Feature> features,
		OrsError error
) {

	public record Feature(
			Properties properties,
			Geometry geometry
	) {
	}

	public record Properties(
			Summary summary
	) {
	}

	public record Summary(
			Double distance,
			Double duration
	) {
	}

	/**
	 * GeoJSON LineString geometry as returned by ORS Directions V2 (GET, default
	 * response format). {@code coordinates} is a list of [lon, lat] pairs, in that
	 * order per the GeoJSON spec -- callers must flip to lat/lng when converting
	 * to {@link com.core.location.api.GeoPoint}.
	 */
	public record Geometry(
			String type,
			List<List<Double>> coordinates
	) {
	}

	public record OrsError(
			Integer code,
			String message
	) {
	}
}

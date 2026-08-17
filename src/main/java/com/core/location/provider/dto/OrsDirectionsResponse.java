package com.core.location.provider.dto;

import java.util.List;

public record OrsDirectionsResponse(
		List<Feature> features,
		OrsError error
) {

	public record Feature(
			Properties properties
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

	public record OrsError(
			Integer code,
			String message
	) {
	}
}

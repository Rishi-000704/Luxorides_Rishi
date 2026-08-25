package com.core.dtos.client.app;

public record TripRatingRequest(
		Integer stars,
		String comment
) {
}

package com.core.dtos.client.app;

import java.time.Instant;

public record TripRatingResponse(
		String dutyId,
		Integer stars,
		String comment,
		Instant createdAt
) {
}

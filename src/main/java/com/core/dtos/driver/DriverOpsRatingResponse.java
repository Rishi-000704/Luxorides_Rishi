package com.core.dtos.driver;

import java.time.Instant;

// null fields mean ops hasn't rated this driver yet -- never a fabricated
// default rating.
public record DriverOpsRatingResponse(
		Integer stars,
		String comment,
		Instant updatedAt,
		String ratedByName
) {
}

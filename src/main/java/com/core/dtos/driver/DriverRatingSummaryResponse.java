package com.core.dtos.driver;

// Real, transparent breakdown -- never a single fabricated number. The
// driver app shows combinedAverageRating (or "not yet rated" when null);
// the component fields exist so ops/support can see exactly what it's made
// of. combinedAverageRating is the simple average of clientAverageRating
// and opsRating when both exist, falls back to whichever one exists when
// only one does, and is null when neither does (see DriverRatingService).
public record DriverRatingSummaryResponse(
		Double clientAverageRating,
		long clientRatingCount,
		Integer opsRating,
		Double combinedAverageRating
) {
}

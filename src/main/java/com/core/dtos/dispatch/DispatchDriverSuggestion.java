package com.core.dtos.dispatch;

public record DispatchDriverSuggestion(
		String driverId,
		String driverName,
		Double distanceKm,
		long completedDuties,
		Double ratingAverage,
		double score
) {
}

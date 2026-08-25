package com.core.dtos.analytics;

public record DriverAnalyticsResponse(
		String driverId,
		String driverName,
		long completedDuties,
		Double ratingAverage,
		long ratingCount
) {
}

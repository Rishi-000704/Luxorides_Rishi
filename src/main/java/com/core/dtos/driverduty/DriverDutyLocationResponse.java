package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutyLocationResponse(
		String dutyId,
		Double latitude,
		Double longitude,
		Double headingDegrees,
		Instant capturedAt,
		Double distanceRemainingKm,
		Double etaMinutes,
		boolean etaEstimated
) {
}

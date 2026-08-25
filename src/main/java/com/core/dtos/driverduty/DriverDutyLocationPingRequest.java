package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutyLocationPingRequest(
		Double latitude,
		Double longitude,
		Double accuracyMeters,
		Double headingDegrees,
		Double speedMps,
		Instant capturedAt
) {
}

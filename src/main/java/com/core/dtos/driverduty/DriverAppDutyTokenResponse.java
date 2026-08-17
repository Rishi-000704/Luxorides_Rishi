package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverAppDutyTokenResponse(
		String token,
		Instant expiresAt
) {
}

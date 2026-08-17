package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutyLinkResponse(
		String dutyId,
		String bookingId,
		String driverName,
		String vehicleNumber,
		String url,
		Instant expiresAt
	) {}

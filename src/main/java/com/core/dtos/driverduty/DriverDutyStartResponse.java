package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutyStartResponse(
		boolean success,
		String status,
		Integer startKm,
		Instant startAt,
		String message
	) {}

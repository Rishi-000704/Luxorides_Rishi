package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutySummaryResponse(
		String bookingId,
		String dutyId,
		String currentRequiredAction,

		String clientName,
		String clientPhone,
		String driverName,
		String vehicleName,
		String vehicleNumber,

		String reportingLocation,
		String dropLocation,
		Instant reportingTime,
		Instant dropTime,

		Integer startKm,
		Integer endKm,
		Instant startAt,
		Instant endAt,

		boolean alreadyStarted,
		boolean alreadyCompleted
	) {}

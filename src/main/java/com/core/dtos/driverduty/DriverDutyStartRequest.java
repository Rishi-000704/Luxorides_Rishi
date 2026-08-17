package com.core.dtos.driverduty;

import java.time.Instant;

import com.core.models.embedded.AddressSnapshot;

public record DriverDutyStartRequest(
		Integer odometerKm,
		AddressSnapshot location,
		Double accuracyMeters,
		Instant locationCapturedAt,
		String notes
) {}
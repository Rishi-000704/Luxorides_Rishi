package com.core.dtos.driverduty;

import java.time.Instant;
import java.util.List;

import com.core.models.embedded.AddressSnapshot;

public record DriverDutyEndRequest(
		Integer odometerKm,
		AddressSnapshot location,
		Double accuracyMeters,
		Instant locationCapturedAt,
		String notes,
		List<DriverDutyExpenseInput> extraCharges
) {}
package com.core.dtos.driverduty;

import java.time.Instant;

import com.core.models.embedded.AddressSnapshot;

public record DriverDutyReturnGarageRequest(
		AddressSnapshot location,
		Double accuracyMeters,
		Instant locationCapturedAt
) {}

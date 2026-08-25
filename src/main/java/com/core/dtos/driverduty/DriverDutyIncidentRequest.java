package com.core.dtos.driverduty;

import java.time.Instant;

import com.core.models.embedded.AddressSnapshot;
import com.core.models.enums.DriverDutyIncidentCategory;

public record DriverDutyIncidentRequest(
		DriverDutyIncidentCategory category,
		String description,
		AddressSnapshot location,
		Instant submittedAt
) {
}

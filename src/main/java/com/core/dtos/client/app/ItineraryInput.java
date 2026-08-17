package com.core.dtos.client.app;

import com.core.dtos.common.AddressSnapshotDTO;
import com.core.models.enums.DutyType;

import jakarta.validation.constraints.NotNull;

public record ItineraryInput(
		@NotNull DutyType dutyType,

		@NotNull AddressSnapshotDTO reportingLocation,

		@NotNull String reportingTime,

		// Transfer only
		AddressSnapshotDTO dropLocation,

		// Outstation only
		Integer bookingDays
		) {

}

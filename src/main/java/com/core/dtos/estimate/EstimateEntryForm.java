package com.core.dtos.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.core.dtos.common.AddressSnapshotDTO;

public record EstimateEntryForm(
		String estimateEntryId,
		String packageId,
		String masterVehicleId,
		Instant reportingTime,
		Instant dropTime,
		AddressSnapshotDTO reportingLocation,
		AddressSnapshotDTO dropLocation,
		Integer runningDays,
		Integer extraChargebleDistance,
		Float extraChargebleTime,
		Boolean nightChargeble,
		List<ExtraChargeForm> charges) {

	public record ExtraChargeForm(
			String description,
			BigDecimal amount) {
	}
}
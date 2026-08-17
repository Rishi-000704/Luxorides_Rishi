package com.core.dtos.estimate;

import java.time.Instant;
import java.util.List;

import com.core.dtos.common.AddressSnapshotDTO;
import com.core.dtos.common.MoneyDTO;
import com.core.dtos.vehicle.MasterVehicleDTO;
import com.core.models.embedded.PackageSnapshot;

public record EstimateEntryDTO(
		String id,
		String estimateEntryId,
		PackageSnapshot pack,
		String masterVehicleId,
		MasterVehicleDTO requestedVehicle,
		Instant reportingTime,
		Instant dropTime,
		AddressSnapshotDTO reportingLocation,
		AddressSnapshotDTO dropLocation,
		Integer runningDays,
		Integer extraChargebleDistance,
		Float extraChargebleTime,
		Boolean nightChargeble,
		List<ExtraChargeDTO> charges,
		MoneyDTO lineTotal,
		Instant createdAt,
		Instant updatedAt,
		String createdBy,
		String updatedBy) {

	public record ExtraChargeDTO(
			String id,
			String description,
			MoneyDTO amount,
			String image) {
	}
}
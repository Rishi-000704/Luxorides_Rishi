package com.core.dtos.booking;

import java.time.Instant;
import java.util.List;

import com.core.dtos.client.ClientDTO;
import com.core.dtos.common.MoneyDTO;
import com.core.dtos.driver.DriverDTO;
import com.core.dtos.vehicle.FleetVehicleDTO;
import com.core.dtos.vehicle.MasterVehicleDTO;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.DutyStatus;

public record BookingEntryDTO(

		String dutyId,
		DutyStatus status,
		PackageSnapshot packageSnapshot,

		String reportingLocation,
		Instant reportingTime,
		Integer startingKM,

		String dropLocation,
		Instant dropTime,
		Integer closingKM,

		Instant startAt,
		Instant endAt,

		String flightNumber,

		Integer runningDays,
		Integer extraChargebleDistance,
		Float extraChargebleTime,
		Boolean nightChargeble,

		String dutySlipImage,

		Money dutyTotal,

		MasterVehicleDTO requestedVehicle,
		FleetVehicleDTO allotedVehicle,
		DriverDTO driver,
		ClientDTO supplier,

		List<ExtraChargeDTO> charges,
		List<String> passengerIds,
		String clientNotes,

		Instant createdAt,
		Instant updatedAt,
		String createdBy,
		String updatedBy
) {
	public record ExtraChargeDTO(
			String id,
			String description,
			MoneyDTO amount,
			String image
	) {
	}
}
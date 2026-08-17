package com.core.dtos.driverduty;

import java.time.Instant;

import com.core.models.embedded.Money;
import com.core.models.enums.DutyStatus;

/*
 * The driver's own view of one of their duties — for the /driver/app dashboard
 * (active list, history list, duty detail). Deliberately separate from
 * DriverDutySummaryResponse (the token-authenticated public view used during
 * execution) since this one is scoped to "duties belonging to the logged-in
 * driver" rather than "the one duty a magic link points at".
 */
public record DutySummaryForDriverDTO(
		String dutyId,
		String bookingId,
		DutyStatus status,

		String clientName,
		String vehicleName,
		String vehicleNumber,

		String reportingLocation,
		String dropLocation,
		Instant reportingTime,
		Instant dropTime,

		Integer startingKM,
		Integer closingKM,
		Instant startAt,
		Instant endAt,

		Money dutyTotal
) {
}

package com.core.dtos.pub;

import java.time.Instant;

import com.core.models.enums.DutyStatus;

/*
 * Deliberately minimal -- this is served with NO authentication (see
 * PublicTripController), so only what's needed to reassure someone tracking
 * a trip is exposed. No phone numbers, no addresses, no booking financials.
 */
public record PublicTripStatusResponse(
		DutyStatus dutyStatus,
		String driverFirstName,
		String vehicleName,
		String vehicleNumber,
		Double latitude,
		Double longitude,
		Double headingDegrees,
		Instant capturedAt,
		Double distanceRemainingKm,
		Double etaMinutes,
		Instant arrivedAtPickupAt
) {
}

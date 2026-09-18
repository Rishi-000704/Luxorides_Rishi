package com.core.dtos.booking;

import java.time.Instant;

/*
 * Real answer to "is this driver/vehicle actually free to take this duty" --
 * checked against BookingEntryRepository's ALLOTTED/RUNNING rows (see
 * BookingService#checkAvailability), never assumed. A false available field
 * is always paired with the specific conflicting duty so an operator sees
 * exactly what they're overriding, not just a generic warning.
 */
public record AvailabilityCheckResponse(
		boolean driverAvailable,
		String driverConflictDutyId,
		Instant driverConflictReportingTime,

		boolean vehicleAvailable,
		String vehicleConflictDutyId,
		Instant vehicleConflictReportingTime
) {
}

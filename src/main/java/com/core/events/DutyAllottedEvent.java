package com.core.events;

import com.core.models.enums.DutyType;

public record DutyAllottedEvent(
		String orgId,
		String recipientEmail,
		String recipientMobileNumber,
		String clientName,

		String bookingId,
		String dutyId,

		DutyType dutyType,
		String reportingDate,
		String reportingLocation,

		String driverName,
		String driverPhone,

		String vehicleName,
		String vehicleType,
		String vehicleNumber
) {
}
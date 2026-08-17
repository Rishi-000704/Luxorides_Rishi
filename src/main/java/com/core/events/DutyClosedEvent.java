package com.core.events;

public record DutyClosedEvent(
		String orgId,
		String recipientEmail,
		String recipientMobileNumber,
		String clientName,

		String bookingId,
		String dutyId,

		String startingTime,
		String reportingTime,
		String dropTime,
		String closingTime,

		String totalDistance
) {
}
package com.core.events;

public record DutyReClosedEvent(
		String orgId,
		String recipientEmail, String recipientMobileNumber, String clientName,

		String bookingId, String dutyId,

		String startingTime, String reportingTime, String dropTime, String closingTime,

		String totalDistance) {}

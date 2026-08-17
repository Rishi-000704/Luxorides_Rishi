package com.core.events;

import java.util.List;

public record BookingConfirmedEvent(
		String orgId,
		String recipientEmail,
		String recipientMobileNumber,
		String clientName,
		String bookingId,
		String totalAmount,
		int totalDuties,
		List<Duty> duties
) {
	public record Duty(
			String dutyId,
			String vehicleName,
			String reportingDate,
			String reportingLocation
	) {
	}
}
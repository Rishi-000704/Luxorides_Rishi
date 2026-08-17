package com.core.events;

import java.util.List;

public record BookingCancelledEvent(
		String orgId,
		String recipientEmail,
		String recipientMobileNumber,
		String clientName,
		String bookingId,
		String totalAmount,
		String cancellationReson,
		int totalDuties,
		List<Duty> duties
) {
	public record Duty(
			String dutyId,
			String vahicleName,
			String reportingDate,
			String reportingLocation
	) {
	}
}
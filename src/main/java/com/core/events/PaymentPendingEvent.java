package com.core.events;

public record PaymentPendingEvent(
		String orgId,

		String recipientEmail, String recipientMobileNumber, String clientName,

		String bookingId,

		String totalAmount, String paidAmount, String pendingAmount) {
}

package com.core.events;

public record RefundCompletedEvent(
		String orgId,
		String recipientEmail, 
		String recipientMobileNumber,
		String clientName,

        String bookingId,

        String refundAmount
        ) {}

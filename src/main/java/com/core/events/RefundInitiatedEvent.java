package com.core.events;

public record RefundInitiatedEvent(
		String orgId,
		String recipientEmail, 
		String recipientMobileNumber,
		String clientName,

        String bookingId,

        String refundAmount,
        String feeAmount
        ) {}

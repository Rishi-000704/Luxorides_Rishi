package com.core.events;

public record PaymentConfirmedEvent(
		String orgId,
		String recipientEmail, 
		String recipientMobileNumber,
		String clientName,

        String bookingId,
        String transactionId,

        String amountPaid,
        String paymentDate,
        String paymentMethod
        ) {}

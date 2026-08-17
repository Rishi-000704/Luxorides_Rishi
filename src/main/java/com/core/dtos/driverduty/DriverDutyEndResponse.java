package com.core.dtos.driverduty;

public record DriverDutyEndResponse(
		boolean success,
		String status,
		DutyCompletionSummary summary,
		PaymentInstruction paymentInstruction,
		String message
	) {}

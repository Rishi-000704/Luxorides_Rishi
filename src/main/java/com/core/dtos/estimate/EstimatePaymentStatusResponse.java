package com.core.dtos.estimate;

import java.time.Instant;

import com.core.models.enums.EstimateStatus;
import com.core.models.enums.PaymentStatus;

public record EstimatePaymentStatusResponse(
		EstimateStatus estimateStatus,
		PaymentStatus paymentStatus,
		boolean paid,
		boolean converted,
		String bookingId,
		Instant paidAt,
		String message) {
}
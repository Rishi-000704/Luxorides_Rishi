package com.core.dtos.payment;

import java.time.Instant;

import com.core.dtos.common.MoneyDTO;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentMode;
import com.core.models.enums.PaymentStatus;

public record PaymentDTO(

		// Identity
		String id,

		// Payment details
		PaymentMode paymentMode, String transactionNumber, Instant transactionDate,

		// Amounts
		MoneyDTO receivedAmount, MoneyDTO tds,

		// Gateway
		PaymentGateway gateway, String gatewayOrderId, String gatewayPaymentId,

		// Status & notes
		PaymentStatus status, String remarks,
		
		Instant createdAt, Instant updatedAt, String createdBy, String updatedBy
		) {
}

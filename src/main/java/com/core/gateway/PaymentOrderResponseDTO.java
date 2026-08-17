package com.core.gateway;

import com.core.models.embedded.Money;
import com.core.models.enums.PaymentGateway;

public record PaymentOrderResponseDTO( PaymentGateway gateway ,// RAZORPAY
		String publicKey, // razorpay key
		String orderId, // gateway order id
		Money amount) {
}

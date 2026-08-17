package com.core.gateway;

import com.core.models.enums.PaymentGateway;

public record VerifyPaymentDTO(
    String bookingId,
    PaymentGateway gateway,

    // Razorpay fields (others can add their own later)
    String orderId,
    String paymentId,
    String signature
) {}


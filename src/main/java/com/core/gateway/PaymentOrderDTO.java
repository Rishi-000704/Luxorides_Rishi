package com.core.gateway;

import com.core.models.enums.PaymentGateway;

public record PaymentOrderDTO(
    String bookingId,
    PaymentGateway gateway
) {}

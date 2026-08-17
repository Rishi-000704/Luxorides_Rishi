package com.core.dtos.booking;

import java.time.Instant;

import com.core.models.embedded.Money;
import com.core.models.enums.PaymentMode;

public record BookingPaymentCommand(
        String paymentId,
        String bookingId,
        PaymentMode paymentMode,
        String transactionNumber,
        Instant transactionDate,
        Money receivedAmount,
        Money tds,
        String remarks
) {}

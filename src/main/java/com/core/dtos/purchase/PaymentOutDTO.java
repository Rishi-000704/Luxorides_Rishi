package com.core.dtos.purchase;

import java.time.Instant;

import com.core.models.embedded.Money;
import com.core.models.enums.PaymentMode;
import com.core.models.enums.PaymentStatus;

public record PaymentOutDTO(
        String id,
        String vendorId,
        String purchaseInvoiceId,

        PaymentMode paymentMode,
        String transactionNumber,
        Instant transactionDate,

        Money paidAmount,
        Money tds,

        String remarks,
        PaymentStatus status,

        Instant createdAt,
        Instant updatedAt
) {
}
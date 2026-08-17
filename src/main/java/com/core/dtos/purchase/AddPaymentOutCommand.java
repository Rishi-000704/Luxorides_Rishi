package com.core.dtos.purchase;

import java.time.Instant;

import com.core.models.embedded.Money;
import com.core.models.enums.PaymentMode;

public record AddPaymentOutCommand(
        String purchaseInvoiceId,
        PaymentMode paymentMode,
        String transactionNumber,
        Instant transactionDate,
        Money paidAmount,
        Money tds,
        String remarks
) {
}
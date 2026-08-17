package com.core.dtos.purchase;

import java.time.Instant;

public record CompletePurchaseInvoiceCommand(
        String vendorInvoiceNumber,
        Instant vendorInvoiceDate,
        Instant dueDate,
        String remarks
) {
}
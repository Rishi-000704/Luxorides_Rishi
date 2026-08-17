package com.core.dtos.purchase;

public record PurchaseInvoiceEntryCommand(
        String bookingEntryId,
        String packageId,
        String remarks
) {
}
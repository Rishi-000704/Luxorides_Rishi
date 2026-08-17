package com.core.dtos.purchase;

import java.time.Instant;
import java.util.List;

import com.core.models.enums.GstType;

public record CreatePurchaseInvoiceDraftCommand(
        String vendorId,
        String vendorBillingEntityId,
        String orgBillingEntityId,
        String vendorInvoiceNumber,
        Instant vendorInvoiceDate,
        Instant invoiceDate,
        Instant dueDate,
        GstType gstType,
        Integer gstRate,
        String remarks,
        List<PurchaseInvoiceEntryCommand> entries
) {
}
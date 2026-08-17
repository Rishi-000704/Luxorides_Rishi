package com.core.dtos.purchase;

import java.time.Instant;
import java.util.List;

import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.PurchaseInvoiceStatus;

public record PurchaseInvoiceDTO(
        String id,
        String orgId,

        String vendorId,
        String vendorName,

        String vendorBillingEntityId,
        String vendorBillingEntityName,

        String orgBillingEntityId,
        String orgBillingEntityName,

        String purchaseInvoiceNumber,
        String vendorInvoiceNumber,
        Instant vendorInvoiceDate,
        Instant invoiceDate,
        Instant dueDate,

        Money subtotal,
        Money taxableAmount,
        GstSnapshot gstSnapshot,
        Money grandTotal,
        Money paidAmount,
        Money balanceAmount,

        PurchaseInvoiceStatus status,
        String remarks,

        List<PurchaseInvoiceEntryDTO> entries,
        List<PaymentOutDTO> payments,

        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
}
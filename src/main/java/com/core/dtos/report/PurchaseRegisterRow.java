package com.core.dtos.report;

import java.math.BigDecimal;
import java.time.Instant;

import com.core.models.enums.PurchaseInvoiceStatus;

public record PurchaseRegisterRow(
        String purchaseInvoiceNumber,
        String vendorInvoiceNumber,
        Instant vendorInvoiceDate,
        Instant invoiceDate,
        String vendorName,
        String vendorGstin,
        String vendorBillingEntity,
        String orgBillingEntity,
        BigDecimal taxableAmount,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal totalGst,
        BigDecimal grandTotal,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        PurchaseInvoiceStatus status
) {
}

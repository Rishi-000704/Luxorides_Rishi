package com.core.dtos.invoice;

import java.math.BigDecimal;
import java.time.Instant;

import com.core.models.enums.InvoiceStatus;

public record InvoiceHeaderDTO(
    String id,
    String invoiceNumber,
    Instant invoiceDate,

    String clientName,
    String clientPhone,

    String billingName,
    String billingGstin,

    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal grandTotal,

    BigDecimal totalPaid,
    BigDecimal balance,

    InvoiceStatus status
) {}
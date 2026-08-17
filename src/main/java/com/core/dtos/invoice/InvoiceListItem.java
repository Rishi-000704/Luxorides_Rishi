package com.core.dtos.invoice;

import java.time.Instant;

import com.core.dtos.common.MoneyDTO;
import com.core.models.enums.InvoiceStatus;

public record InvoiceListItem(
    String id,
    String invoiceNumber,
    Instant invoiceDate,
    String clientName,
    String corporateName,
    MoneyDTO grandTotal,
    MoneyDTO totalPaid,
    MoneyDTO balanceAmount,
    InvoiceStatus status
) {}
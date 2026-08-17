package com.core.dtos.invoice;

import java.math.BigDecimal;

public record InvoiceEntryDTO(
    String dutyId,
    String vehicle,
    String driver,
    BigDecimal dutyTotal
) {}
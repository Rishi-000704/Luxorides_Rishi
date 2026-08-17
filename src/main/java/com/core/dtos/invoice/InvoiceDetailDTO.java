package com.core.dtos.invoice;

import java.util.List;

public record InvoiceDetailDTO(
    InvoiceHeaderDTO header,
    List<InvoiceEntryDTO> entries,
    List<PaymentDTO> payments
) {}
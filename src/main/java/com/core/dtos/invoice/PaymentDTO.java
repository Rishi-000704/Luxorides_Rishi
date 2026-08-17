package com.core.dtos.invoice;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDTO(
    String id,
    BigDecimal amount,
    Instant date
) {}
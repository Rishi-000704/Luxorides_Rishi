package com.core.dtos.common;

import java.math.BigDecimal;

import com.core.models.enums.Currency;

public record MoneyDTO(
		BigDecimal amount, Currency currency
		) {

}

package com.core.models.embedded;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.core.models.enums.Currency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Money {

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private Currency currency;

	public static Money INR(BigDecimal amount) {
		return new Money(amount.setScale(2, RoundingMode.HALF_UP), Currency.INR);
	}

	public static Money INR(Float amount) {
		return new Money(new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP), Currency.INR);
	}
}

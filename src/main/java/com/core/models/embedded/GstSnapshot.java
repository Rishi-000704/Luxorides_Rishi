package com.core.models.embedded;

import java.math.BigDecimal;

import com.core.models.enums.GstType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GstSnapshot {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GstType gstType;

	private Integer gstRate;

	@Column(precision = 15, scale = 2)
	private BigDecimal igstAmount;

	@Column(precision = 15, scale = 2)
	private BigDecimal cgstAmount;

	@Column(precision = 15, scale = 2)
	private BigDecimal sgstAmount;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal totalTax;

	public static GstSnapshot of(GstType gstType, BigDecimal taxableAmount, Integer gstRate) {
		BigDecimal tax = taxableAmount.multiply(BigDecimal.valueOf(gstRate)).divide(BigDecimal.valueOf(100));

		if (gstType == GstType.IGST) {
			return new GstSnapshot(gstType, gstRate, tax, BigDecimal.ZERO, BigDecimal.ZERO, tax);
		}

		if (gstType == GstType.CGST_SGST) {
			BigDecimal half = tax.divide(BigDecimal.valueOf(2));
			return new GstSnapshot(gstType, gstRate, BigDecimal.ZERO, half, half, tax);
		}

		return new GstSnapshot(GstType.EXEMPT, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
	}
}

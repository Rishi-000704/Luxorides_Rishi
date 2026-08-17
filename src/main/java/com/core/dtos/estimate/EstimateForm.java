package com.core.dtos.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.core.models.enums.GstType;

public record EstimateForm(
		String estimateId,
		Instant estimateDate,
		Instant validTill,
		String clientId,
		String clientBillingEntityId,
		BigDecimal discountAmount,
		BigDecimal advanceAmount,
		GstType gstType,
		Integer gstRate,
		String remarks,
		List<EstimateEntryForm> entries) {
}
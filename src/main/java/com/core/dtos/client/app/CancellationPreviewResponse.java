package com.core.dtos.client.app;

import java.math.BigDecimal;

public record CancellationPreviewResponse(
		boolean withinFreeWindow,
		BigDecimal paidAmount,
		BigDecimal feeAmount,
		BigDecimal refundAmount,
		Integer freeWindowHours,
		BigDecimal feePercent
) {
}

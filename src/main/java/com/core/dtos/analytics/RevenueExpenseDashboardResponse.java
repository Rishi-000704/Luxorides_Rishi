package com.core.dtos.analytics;

import java.math.BigDecimal;

public record RevenueExpenseDashboardResponse(
		BigDecimal revenue,
		BigDecimal expense,
		BigDecimal netProfit
) {
}

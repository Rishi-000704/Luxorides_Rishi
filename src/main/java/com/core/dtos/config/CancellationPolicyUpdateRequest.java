package com.core.dtos.config;

import java.math.BigDecimal;

public record CancellationPolicyUpdateRequest(
		Integer cancellationFreeWindowHours,
		BigDecimal cancellationFeePercent
) {
}

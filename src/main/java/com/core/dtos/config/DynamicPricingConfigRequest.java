package com.core.dtos.config;

import java.math.BigDecimal;

public record DynamicPricingConfigRequest(
		boolean enabled,
		BigDecimal maxMultiplier
) {
}

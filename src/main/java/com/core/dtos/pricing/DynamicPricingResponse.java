package com.core.dtos.pricing;

import java.math.BigDecimal;

public record DynamicPricingResponse(
		boolean enabled,
		long pendingDuties,
		long idleDrivers,
		BigDecimal demandSupplyRatio,
		BigDecimal multiplier,
		BigDecimal maxMultiplier
) {
}

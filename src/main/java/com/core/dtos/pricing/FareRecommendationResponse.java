package com.core.dtos.pricing;

import java.math.BigDecimal;

public record FareRecommendationResponse(
		boolean sufficientData,
		int sampleSize,
		BigDecimal recommendedFare,
		BigDecimal minObservedFare,
		BigDecimal maxObservedFare
) {
}

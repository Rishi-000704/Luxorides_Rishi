package com.core.dtos.estimate;

import java.time.Instant;

import com.core.models.enums.EstimateLinkStatus;

public record EstimateLinkResponse(
		String estimateId,
		String url,
		EstimateLinkStatus status,
		Instant expiresAt,
		Instant lastViewedAt,
		Instant paidAt) {
}
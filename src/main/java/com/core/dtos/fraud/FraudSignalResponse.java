package com.core.dtos.fraud;

import java.time.Instant;

import com.core.models.enums.FraudSignalStatus;
import com.core.models.enums.FraudSignalType;

public record FraudSignalResponse(
		String id,
		FraudSignalType type,
		String clientId,
		String driverId,
		String bookingId,
		String dutyId,
		String description,
		FraudSignalStatus status,
		String reviewedBy,
		Instant reviewedAt,
		Instant createdAt
) {
}

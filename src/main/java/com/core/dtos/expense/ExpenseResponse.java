package com.core.dtos.expense;

import java.math.BigDecimal;
import java.time.Instant;

import com.core.models.enums.ExpenseCategory;

public record ExpenseResponse(
		String id,
		ExpenseCategory category,
		BigDecimal amount,
		Instant incurredAt,
		String fleetVehicleId,
		String driverId,
		String remarks,
		Instant createdAt
) {
}

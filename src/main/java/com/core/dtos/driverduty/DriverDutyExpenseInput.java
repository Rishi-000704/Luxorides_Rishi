package com.core.dtos.driverduty;

import java.math.BigDecimal;

import com.core.models.enums.DriverDutyExpenseType;

public record DriverDutyExpenseInput(
		DriverDutyExpenseType type,
		BigDecimal amount,
		String description
	) {}

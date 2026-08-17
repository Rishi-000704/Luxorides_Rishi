package com.core.dtos.driverduty;

import java.math.BigDecimal;
import java.time.Instant;

public record DutyCompletionSummary(
		String bookingId,
		String dutyId,
		Integer startKm,
		Integer endKm,
		Integer totalKm,
		Instant startAt,
		Instant endAt,
		BigDecimal extraChargesTotal,
		BigDecimal bookingTotal,
		BigDecimal amountToCollect
	) {}

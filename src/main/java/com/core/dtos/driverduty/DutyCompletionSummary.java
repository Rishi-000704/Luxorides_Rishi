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
		BigDecimal amountToCollect,
		// -- Below: precise, backend-computed display figures (distinct from the
		// -- rounded, billing-oriented startKm/endKm/totalKm above, which are left
		// -- untouched to avoid perturbing existing fare/odometer semantics). --
		Integer actualDrivenKm,
		Double projectedTotalKm,
		ReturnRouteEstimate returnRoute
	) {}

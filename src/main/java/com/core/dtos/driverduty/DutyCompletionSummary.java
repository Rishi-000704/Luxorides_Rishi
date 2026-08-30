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
		ReturnRouteEstimate returnRoute,
		// -- Package/rate-card breakdown behind dutyTotal (base fare, included km/time,
		// -- extra km/time chargeable + their rates) -- see PackageFareBreakdownFactory.
		PackageFareBreakdown fareBreakdown,
		// -- Booking-level GST already folded into bookingTotal/amountToCollect by
		// -- BookingUtil.calculateTotalAmount. Zero/null for GST-exempt orgs (today's
		// -- seeded default). Surfaced so a driver-facing fare breakdown that lists
		// -- bookingTotal's components doesn't appear to under-add for a GST-registered
		// -- org -- never itself added into any total, only displayed.
		BigDecimal gstAmount,
		Integer gstRatePercent
	) {}

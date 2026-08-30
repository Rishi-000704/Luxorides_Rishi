package com.core.util;

import java.math.BigDecimal;
import java.time.Duration;

import com.core.dtos.driverduty.PackageFareBreakdown;
import com.core.models.BookingEntry;
import com.core.models.embedded.PackageSnapshot;

/**
 * Builds a {@link PackageFareBreakdown} from a duty entry that has already gone through
 * {@link BookingUtil#calculateTotal(BookingEntry)}. Pure read-out of entry.getPack() and the
 * extra-chargeable distance/time BookingUtil already set on the entry -- this class performs
 * no fare calculation of its own, so it can never drift from the amount actually billed.
 */
public final class PackageFareBreakdownFactory {

	private PackageFareBreakdownFactory() {
	}

	public static PackageFareBreakdown build(BookingEntry entry) {
		PackageSnapshot pack = entry.getPack();

		if (pack == null) {
			return null;
		}

		Integer extraDistanceKm = entry.getExtraChargebleDistance() != null ? entry.getExtraChargebleDistance() : 0;
		Float extraTimeHoursRaw = entry.getExtraChargebleTime() != null ? entry.getExtraChargebleTime() : 0f;

		BigDecimal extraDistanceRate = pack.getExtraPerKM() != null ? pack.getExtraPerKM().getAmount() : null;
		BigDecimal extraTimeRate = pack.getExtraPerHS() != null ? pack.getExtraPerHS().getAmount() : null;

		BigDecimal extraDistanceCharge = extraDistanceKm > 0 && extraDistanceRate != null
				? extraDistanceRate.multiply(BigDecimal.valueOf(extraDistanceKm))
				: BigDecimal.ZERO;

		BigDecimal extraTimeCharge = extraTimeHoursRaw > 0 && extraTimeRate != null
				? extraTimeRate.multiply(BigDecimal.valueOf(extraTimeHoursRaw))
				: BigDecimal.ZERO;

		Long projectedTotalDurationSeconds = entry.getStartAt() != null && entry.getEndAt() != null
				? Duration.between(entry.getStartAt(), entry.getEndAt()).getSeconds()
				: null;

		return new PackageFareBreakdown(
				pack.getDutyType() != null ? pack.getDutyType().name() : null,
				pack.getUnit(),
				pack.getDistance(),
				pack.getTime(),
				pack.getBaseFare() != null ? pack.getBaseFare().getAmount() : null,
				extraDistanceKm,
				extraDistanceRate,
				extraDistanceCharge,
				extraTimeHoursRaw.doubleValue(),
				extraTimeRate,
				extraTimeCharge,
				projectedTotalDurationSeconds
		);
	}
}

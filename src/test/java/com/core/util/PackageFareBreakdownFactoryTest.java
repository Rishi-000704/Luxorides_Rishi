package com.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.core.dtos.driverduty.PackageFareBreakdown;
import com.core.models.BookingEntry;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.DutyType;
import com.core.models.enums.PackageScope;

/*
 * Covers the four fare-math cases from the driver-app final-fare spec: within package,
 * distance-only overage, time-only overage, and both -- run through the REAL
 * BookingUtil.calculateTotal (not reimplemented here) so this also guards against
 * PackageFareBreakdownFactory's read-out drifting from what actually gets billed.
 *
 * Package: 8 hrs / 80 km, base fare Rs 4500, extra Rs 18/km, extra Rs 180/hr.
 */
class PackageFareBreakdownFactoryTest {

	private static final Instant START = Instant.parse("2026-01-01T09:00:00Z");

	@Test
	void withinPackage_noExtraCharges() {
		// 36 km driven (well under 80), 6 hours elapsed (under 8) -- mirrors the
		// A->B->C + estimated C->A projected total (36.1 km) from the spec's example.
		BookingEntry entry = duty(36, minutes(6 * 60));

		PackageFareBreakdown breakdown = PackageFareBreakdownFactory.build(entry);

		assertEquals(0, breakdown.extraDistanceKm());
		assertEquals(BigDecimal.ZERO, breakdown.extraDistanceCharge());
		assertEquals(0.0, breakdown.extraTimeHours());
		assertEquals(BigDecimal.ZERO, breakdown.extraTimeCharge());
		assertEquals(BigDecimal.valueOf(4500).setScale(2), breakdown.baseFareAmount().setScale(2));
		assertEquals(80, breakdown.includedDistanceKm());
		assertEquals(8, breakdown.includedTimeUnits());
	}

	@Test
	void distanceExceedsPackage_extraDistanceChargedOnly() {
		// 95 km driven (15 over 80), 6 hours elapsed (under 8).
		BookingEntry entry = duty(95, minutes(6 * 60));

		PackageFareBreakdown breakdown = PackageFareBreakdownFactory.build(entry);

		assertEquals(15, breakdown.extraDistanceKm());
		assertEquals(0, BigDecimal.valueOf(270).compareTo(breakdown.extraDistanceCharge()));
		assertEquals(0.0, breakdown.extraTimeHours());
		assertEquals(BigDecimal.ZERO, breakdown.extraTimeCharge());
	}

	@Test
	void timeExceedsPackage_extraTimeChargedOnly() {
		// 50 km driven (under 80), 8 hr 20 min elapsed (20 min over -> quarter-hour billing = 0.5 hr).
		BookingEntry entry = duty(50, minutes(8 * 60 + 20));

		PackageFareBreakdown breakdown = PackageFareBreakdownFactory.build(entry);

		assertEquals(0, breakdown.extraDistanceKm());
		assertEquals(BigDecimal.ZERO, breakdown.extraDistanceCharge());
		assertEquals(0.5, breakdown.extraTimeHours());
		assertEquals(0, BigDecimal.valueOf(90).compareTo(breakdown.extraTimeCharge()));
	}

	@Test
	void bothDistanceAndTimeExceedPackage_bothCharged() {
		// 95 km driven (15 over), 8 hr 20 min elapsed (0.5 hr over).
		BookingEntry entry = duty(95, minutes(8 * 60 + 20));

		PackageFareBreakdown breakdown = PackageFareBreakdownFactory.build(entry);

		assertEquals(15, breakdown.extraDistanceKm());
		assertEquals(0, BigDecimal.valueOf(270).compareTo(breakdown.extraDistanceCharge()));
		assertEquals(0.5, breakdown.extraTimeHours());
		assertEquals(0, BigDecimal.valueOf(90).compareTo(breakdown.extraTimeCharge()));
	}

	@Test
	void noPackageSnapshot_returnsNullRatherThanFabricating() {
		BookingEntry entry = new BookingEntry();
		entry.setPack(null);

		assertNull(PackageFareBreakdownFactory.build(entry));
	}

	private static long minutes(long totalMinutes) {
		return totalMinutes;
	}

	private static BookingEntry duty(int drivenKm, long durationMinutes) {
		PackageSnapshot pack = new PackageSnapshot();
		pack.setPackageId("pkg-1");
		pack.setScope(PackageScope.CLIENT);
		pack.setDutyType(DutyType.LOCAL);
		pack.setTime(8);
		pack.setDistance(80);
		pack.setUnit("Hours");
		pack.setBaseFare(Money.INR(BigDecimal.valueOf(4500)));
		pack.setExtraPerKM(Money.INR(BigDecimal.valueOf(18)));
		pack.setExtraPerHS(Money.INR(BigDecimal.valueOf(180)));
		pack.setNightCharge(Money.INR(BigDecimal.ZERO));

		BookingEntry entry = new BookingEntry();
		entry.setPack(pack);
		entry.setStartingKM(1000);
		entry.setClosingKM(1000 + drivenKm);
		entry.setStartAt(START);
		entry.setEndAt(START.plusSeconds(durationMinutes * 60));
		entry.setNightChargeble(false);
		entry.setCharges(new ArrayList<>());
		entry.setStatus(DutyStatus.COMPLETED);

		return BookingUtil.calculateTotal(entry);
	}
}

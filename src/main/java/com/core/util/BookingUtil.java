package com.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.ExtraCharge;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.DutyStatus;

public final class BookingUtil {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

	private BookingUtil() {
	}

	// ---------------------------------------------------------------------
	// IDs
	// ---------------------------------------------------------------------

	public static String generateBookingId() {
		int suffix = ThreadLocalRandom.current().nextInt(100, 1000);
		return "B" + LocalDateTime.now().format(FORMATTER) + suffix;
	}

	public static String generateDutyId(Booking booking) {
		return booking.getBookingId() + "-" + (booking.getEntries().size() + 1);
	}

	// ---------------------------------------------------------------------
	// DUTY TOTAL (STATUS AWARE)
	// ---------------------------------------------------------------------

	public static BookingEntry calculateTotal(BookingEntry entry) {

		if (entry.getStatus() == DutyStatus.COMPLETED) {
			return calculateFinalTotal(entry);
		}

		return calculateBaseFareOnly(entry);
	}

	// ---------------------------------------------------------------------
	// BASE FARE ONLY (PRE-DUTY)
	// ---------------------------------------------------------------------

	private static BookingEntry calculateBaseFareOnly(BookingEntry entry) {

		PackageSnapshot pack = entry.getPack();

		if (pack == null || pack.getBaseFare() == null) {
			throw new BusinessException(ErrorCode.PACKAGE_NOT_FOUND, "Package/basefare missing");
		}

		BigDecimal baseFare = pack.getBaseFare().getAmount().setScale(0, RoundingMode.HALF_UP);

		entry.setRunningDays(null);
		entry.setExtraChargebleDistance(0);
		entry.setExtraChargebleTime(0f);
		entry.setDutyTotal(Money.INR(baseFare));

		return entry;
	}

	// ---------------------------------------------------------------------
	// FINAL DUTY CALCULATION (POST-DUTY)
	// ---------------------------------------------------------------------

	private static BookingEntry calculateFinalTotal(BookingEntry entry) {

		validateEntryForFinalCalculation(entry);

		Instant startTime = entry.getStartAt();
		Instant endTime = entry.getEndAt();

		LocalDate startDate = startTime.atZone(ZONE).toLocalDate();
		LocalDate endDate = endTime.atZone(ZONE).toLocalDate();

		int runningDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
		long runningMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
		int runningDistance = entry.getClosingKM() - entry.getStartingKM();

		BigDecimal extraChargeableTime = BigDecimal.ZERO;
		int extraChargeableDistance = 0;

		PackageSnapshot pack = entry.getPack();

		// ---------------- HOURLY DUTY ----------------
		if (isHourlyDuty(entry)) {

			long allowedMinutes = pack.getTime() * 60L;
			long extraMinutes = runningMinutes - allowedMinutes;

			if (extraMinutes > 0) {
				extraChargeableTime = BigDecimal.valueOf(toQuarterHourBilling(extraMinutes));
			}

			extraChargeableDistance = Math.max(runningDistance - pack.getDistance(), 0);
		}
		// ---------------- DAY / OUTSTATION ----------------
		else {

			float allowedHours = runningDays * 24f;

			if ((runningMinutes / 60f) > allowedHours) {
				runningDays += 1;
			}

			extraChargeableDistance = Math.max(runningDistance - (pack.getDistance() * runningDays), 0);
		}

		entry.setRunningDays(runningDays);
		entry.setExtraChargebleTime(extraChargeableTime.floatValue());
		entry.setExtraChargebleDistance(extraChargeableDistance);

		// ---------------- DUTY TOTAL ----------------

		BigDecimal dutyTotal;

		if (isHourlyDuty(entry)) {
			// Hourly = flat base fare (no day multiplication)
			dutyTotal = pack.getBaseFare().getAmount();
		} else {
			// Day-based = multiply by running days
			dutyTotal = pack.getBaseFare().getAmount().multiply(BigDecimal.valueOf(runningDays));
		}

		if (extraChargeableDistance > 0) {
			dutyTotal = dutyTotal
					.add(pack.getExtraPerKM().getAmount().multiply(BigDecimal.valueOf(extraChargeableDistance)));
		}

		if (extraChargeableTime.compareTo(BigDecimal.ZERO) > 0) {
			dutyTotal = dutyTotal.add(pack.getExtraPerHS().getAmount().multiply(extraChargeableTime));
		}

		if (Boolean.TRUE.equals(entry.getNightChargeble())) {
			dutyTotal = dutyTotal.add(pack.getNightCharge().getAmount());
		}

		if (entry.getCharges() != null) {
			for (ExtraCharge ec : entry.getCharges()) {
				dutyTotal = dutyTotal.add(ec.getAmount().getAmount());
			}
		}

		// 🔒 FINAL ROUNDING — accounting safe
		dutyTotal = dutyTotal.setScale(0, RoundingMode.HALF_UP);

		entry.setDutyTotal(Money.INR(dutyTotal));
		return entry;
	}

	// ---------------------------------------------------------------------
	// VALIDATION (FINAL ONLY)
	// ---------------------------------------------------------------------

	private static void validateEntryForFinalCalculation(BookingEntry entry) {

		if (entry.getStartAt() == null || entry.getEndAt() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty Start/End time missing");
		}

		if (entry.getStartingKM() == null || entry.getClosingKM() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "KM readings missing");
		}

		if (entry.getClosingKM() < entry.getStartingKM()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Closing KM less than starting KM");
		}
	}

	// ---------------------------------------------------------------------
	// TIME BILLING (QUARTER HOUR)
	// ---------------------------------------------------------------------

	private static float toQuarterHourBilling(long extraMinutes) {

		if (extraMinutes <= 0)
			return 0f;

		long fullHours = extraMinutes / 60;
		long remainingMinutes = extraMinutes % 60;

		if (remainingMinutes == 0) {
			return fullHours;
		}

		float quarter;

		if (remainingMinutes <= 15)
			quarter = 0.25f;
		else if (remainingMinutes <= 30)
			quarter = 0.50f;
		else if (remainingMinutes <= 45)
			quarter = 0.75f;
		else
			quarter = 1.00f;

		return fullHours + quarter;
	}

	private static boolean isHourlyDuty(BookingEntry entry) {
		return entry.getPack().getUnit() != null && entry.getPack().getUnit().toLowerCase().startsWith("h");
	}

	// ---------------------------------------------------------------------
	// BOOKING TOTALS
	// ---------------------------------------------------------------------

	public static Booking calculateTotalAmount(Booking booking) {

		if (booking == null || booking.getEntries() == null || booking.getEntries().isEmpty()) {
			return booking;
		}

		BigDecimal subTotal = BigDecimal.ZERO;

		for (BookingEntry entry : booking.getEntries()) {

			if (entry.getDutyTotal() == null || entry.getDutyTotal().getAmount() == null) {
				throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty total missing for booking entry: " + entry.getId());
			}

			subTotal = subTotal.add(entry.getDutyTotal().getAmount());
		}

		subTotal = subTotal.setScale(0, RoundingMode.HALF_UP);

		BigDecimal discountAmount = BigDecimal.ZERO;

		if (booking.getDiscount() != null && booking.getDiscount().getAmount() != null) {
			discountAmount = booking.getDiscount().getAmount();
		}

		if (discountAmount.signum() < 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Discount cannot be negative");
		}

		if (discountAmount.compareTo(subTotal) > 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Discount cannot be greater than booking subtotal");
		}

		BigDecimal taxableAmount = subTotal.subtract(discountAmount).setScale(0, RoundingMode.HALF_UP);

		GstSnapshot existing = booking.getGstSnapshot();

		if (existing == null || existing.getGstType() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "GST configuration missing");
		}

		GstSnapshot gstSnapshot =
				GstSnapshot.of(existing.getGstType(), taxableAmount, existing.getGstRate());

		BigDecimal grandTotal =
				taxableAmount.add(gstSnapshot.getTotalTax())
						.setScale(0, RoundingMode.HALF_UP);

		booking.setDiscount(Money.INR(discountAmount));
		booking.setGstSnapshot(gstSnapshot);
		booking.setTotal(Money.INR(grandTotal));

		return booking;
	}
}

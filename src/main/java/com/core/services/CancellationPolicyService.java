package com.core.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Org;
import com.core.models.Payment;
import com.core.models.enums.PaymentStatus;

/*
 * Real, deterministic computation of a cancellation's fee/refund split
 * against the org's configured policy (Org.cancellationFreeWindowHours /
 * cancellationFeePercent) -- never a guessed or hardcoded number. An org
 * that hasn't configured a policy (both fields null) is treated as
 * "cancellation is always free", matching the system's behavior before
 * this feature existed.
 */
@Service
public class CancellationPolicyService {

	public record Evaluation(
			boolean withinFreeWindow,
			BigDecimal paidAmount,
			BigDecimal feeAmount,
			BigDecimal refundAmount,
			Integer freeWindowHours,
			BigDecimal feePercent
	) {
	}

	public Evaluation evaluate(Booking booking, Org org) {
		BigDecimal paidAmount = confirmedPaidAmount(booking);

		Integer freeWindowHours = org.getCancellationFreeWindowHours();
		BigDecimal feePercent = org.getCancellationFeePercent();

		if (freeWindowHours == null || feePercent == null || feePercent.signum() <= 0) {
			return new Evaluation(true, paidAmount, BigDecimal.ZERO, paidAmount, freeWindowHours, feePercent);
		}

		Instant earliestReporting = earliestReportingTime(booking);

		boolean withinFreeWindow = earliestReporting == null
				|| Instant.now().plusSeconds(freeWindowHours * 3600L).isBefore(earliestReporting);

		if (withinFreeWindow) {
			return new Evaluation(true, paidAmount, BigDecimal.ZERO, paidAmount, freeWindowHours, feePercent);
		}

		BigDecimal feeAmount = paidAmount
				.multiply(feePercent)
				.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

		BigDecimal refundAmount = paidAmount.subtract(feeAmount);
		if (refundAmount.signum() < 0) {
			refundAmount = BigDecimal.ZERO;
		}

		return new Evaluation(false, paidAmount, feeAmount, refundAmount, freeWindowHours, feePercent);
	}

	private BigDecimal confirmedPaidAmount(Booking booking) {
		BigDecimal total = BigDecimal.ZERO;

		if (booking.getPayments() == null) {
			return total;
		}

		for (Payment p : booking.getPayments()) {
			if (p.getStatus() == PaymentStatus.CONFIRMED) {
				total = total.add(p.getReceivedAmount().getAmount());
			}
		}

		return total;
	}

	private Instant earliestReportingTime(Booking booking) {
		if (booking.getEntries() == null || booking.getEntries().isEmpty()) {
			return null;
		}

		return booking.getEntries().stream()
				.map(BookingEntry::getReportingTime)
				.filter(t -> t != null)
				.min(Instant::compareTo)
				.orElse(null);
	}
}

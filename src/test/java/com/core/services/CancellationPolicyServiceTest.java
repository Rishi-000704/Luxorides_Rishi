package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Org;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.enums.PaymentStatus;

/*
 * Covers the cancellation-policy audit's Phase 2 correctness requirements:
 * free-window enforcement, fee calculation, refund calculation, and the
 * documented safe defaults for an unconfigured org / a booking with no
 * recorded reporting time. A pure function (Booking/Org in, Evaluation out)
 * -- no mocking needed.
 */
class CancellationPolicyServiceTest {

	private final CancellationPolicyService service = new CancellationPolicyService();

	private Org orgWithPolicy(int freeWindowHours, String feePercent) {
		Org org = new Org();
		org.setCancellationFreeWindowHours(freeWindowHours);
		org.setCancellationFeePercent(new BigDecimal(feePercent));
		return org;
	}

	private Booking bookingWithConfirmedPayment(BigDecimal amount, Instant reportingTime) {
		Booking booking = new Booking();

		Payment confirmed = new Payment();
		confirmed.setStatus(PaymentStatus.CONFIRMED);
		confirmed.setReceivedAmount(Money.INR(amount));
		booking.setPayments(List.of(confirmed));

		if (reportingTime != null) {
			BookingEntry entry = new BookingEntry();
			entry.setReportingTime(reportingTime);
			booking.setEntries(List.of(entry));
		} else {
			booking.setEntries(List.of());
		}

		return booking;
	}

	@Test
	void cancellationInsideFreeWindow_isFullyRefunded_noFee() {
		Org org = orgWithPolicy(24, "20.00");
		Booking booking = bookingWithConfirmedPayment(new BigDecimal("1000.00"), Instant.now().plus(48, ChronoUnit.HOURS));

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertTrue(evaluation.withinFreeWindow());
		assertEquals(0, BigDecimal.ZERO.compareTo(evaluation.feeAmount()));
		assertEquals(0, new BigDecimal("1000.00").compareTo(evaluation.refundAmount()));
	}

	@Test
	void cancellationAfterFreeWindow_appliesFee() {
		Org org = orgWithPolicy(24, "20.00");
		Booking booking = bookingWithConfirmedPayment(new BigDecimal("1000.00"), Instant.now().plus(2, ChronoUnit.HOURS));

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertTrue(!evaluation.withinFreeWindow());
		assertEquals(0, new BigDecimal("200.00").compareTo(evaluation.feeAmount()));
		assertEquals(0, new BigDecimal("800.00").compareTo(evaluation.refundAmount()));
	}

	@Test
	void feeCalculation_roundsToTwoDecimalPlaces() {
		Org org = orgWithPolicy(24, "33.33");
		Booking booking = bookingWithConfirmedPayment(new BigDecimal("100.00"), Instant.now().plus(1, ChronoUnit.HOURS));

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertEquals(0, new BigDecimal("33.33").compareTo(evaluation.feeAmount()));
		assertEquals(0, new BigDecimal("66.67").compareTo(evaluation.refundAmount()));
	}

	@Test
	void feeCannotExceedPaidAmount_refundNeverGoesNegative() {
		Org org = orgWithPolicy(24, "150.00"); // misconfigured (>100%) org policy
		Booking booking = bookingWithConfirmedPayment(new BigDecimal("100.00"), Instant.now().plus(1, ChronoUnit.HOURS));

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertEquals(0, BigDecimal.ZERO.compareTo(evaluation.refundAmount()));
	}

	@Test
	void unconfiguredOrgPolicy_isAlwaysFree_matchingPreFeatureBehavior() {
		Org org = new Org(); // freeWindowHours/feePercent both null
		Booking booking = bookingWithConfirmedPayment(new BigDecimal("500.00"), Instant.now().plus(1, ChronoUnit.HOURS));

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertTrue(evaluation.withinFreeWindow());
		assertEquals(0, new BigDecimal("500.00").compareTo(evaluation.refundAmount()));
	}

	@Test
	void zeroFeePercent_isTreatedAsAlwaysFree() {
		Org org = orgWithPolicy(24, "0.00");
		Booking booking = bookingWithConfirmedPayment(new BigDecimal("500.00"), Instant.now().plus(1, ChronoUnit.HOURS));

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertTrue(evaluation.withinFreeWindow());
		assertEquals(0, new BigDecimal("500.00").compareTo(evaluation.refundAmount()));
	}

	/*
	 * Safe default (documented assumption): a booking with no recorded
	 * reporting time yet (e.g. cancelled before any duty entry exists) cannot
	 * be judged "close to departure", so it is treated as within the free
	 * window rather than guessing a fee against a trip that isn't scheduled.
	 */
	@Test
	void noReportingTimeRecorded_treatedAsWithinFreeWindow() {
		Org org = orgWithPolicy(24, "20.00");
		Booking booking = bookingWithConfirmedPayment(new BigDecimal("500.00"), null);

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertTrue(evaluation.withinFreeWindow());
	}

	@Test
	void exactlyAtFreeWindowBoundary_isTreatedAsFeeApplicable() {
		Org org = orgWithPolicy(24, "20.00");
		// Reporting time exactly 24h away -- now + 24h is NOT strictly before it,
		// so the safest (fee-applying) branch wins at the boundary.
		Booking booking = bookingWithConfirmedPayment(new BigDecimal("100.00"), Instant.now().plus(24, ChronoUnit.HOURS));

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertTrue(!evaluation.withinFreeWindow());
	}

	@Test
	void onlyConfirmedPayments_countTowardPaidAmount() {
		Org org = orgWithPolicy(24, "20.00");
		Booking booking = new Booking();

		Payment confirmed = new Payment();
		confirmed.setStatus(PaymentStatus.CONFIRMED);
		confirmed.setReceivedAmount(Money.INR(new BigDecimal("500.00")));

		Payment initiated = new Payment();
		initiated.setStatus(PaymentStatus.INITIATED);
		initiated.setReceivedAmount(Money.INR(new BigDecimal("500.00")));

		booking.setPayments(List.of(confirmed, initiated));
		booking.setEntries(List.of());

		CancellationPolicyService.Evaluation evaluation = service.evaluate(booking, org);

		assertEquals(0, new BigDecimal("500.00").compareTo(evaluation.paidAmount()));
	}
}

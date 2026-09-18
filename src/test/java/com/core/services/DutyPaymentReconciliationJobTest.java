package com.core.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Booking;
import com.core.models.Payment;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.PaymentRepository;

/*
 * P1.10 -- proves the flat 7s-per-tick Razorpay poll was replaced with a
 * per-payment exponential backoff (webhook remains the primary confirmation
 * path; this job is only the fallback). A mutable Clock stands in for real
 * elapsed time so the backoff schedule is verified deterministically instead
 * of via real sleeps.
 */
class DutyPaymentReconciliationJobTest {

	private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
	private final RazorpayPaymentService razorpayPaymentService = mock(RazorpayPaymentService.class);

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
	private final Clock clock = new Clock() {
		@Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(java.time.ZoneId zone) { return this; }
		@Override public Instant instant() { return now.get(); }
	};

	private final DutyPaymentReconciliationJob job =
			new DutyPaymentReconciliationJob(paymentRepository, razorpayPaymentService, clock);

	private Payment qrPayment(String id, String orgId, String bookingId, String dutyId) {
		Booking booking = new Booking();
		booking.setBookingId(bookingId);

		Payment payment = new Payment();
		payment.setId(id);
		payment.setOrgId(orgId);
		payment.setBooking(booking);
		payment.setCollectionContext("DRIVER_DUTY_QR");
		payment.setCollectionContextId(dutyId);
		payment.setStatus(PaymentStatus.INITIATED);
		payment.setGateway(PaymentGateway.RAZORPAY);
		return payment;
	}

	private void stubOutstanding(List<Payment> payments) {
		when(paymentRepository.findAllByCollectionContextAndStatusAndGatewayAndExpiresAtAfter(
				eq("DRIVER_DUTY_QR"), eq(PaymentStatus.INITIATED), eq(PaymentGateway.RAZORPAY), any(Instant.class)))
				.thenReturn(payments);
	}

	@Test
	void firstTick_checksANewlyOutstandingPayment() throws Exception {
		Payment p = qrPayment("p1", "org-1", "booking-1", "duty-1");
		stubOutstanding(List.of(p));

		job.reconcileOutstandingQrPayments();

		verify(razorpayPaymentService, times(1)).isPaidByQR("org-1", "booking-1", "duty-1");
	}

	@Test
	void secondTick_withinBackoffWindow_doesNotRecheck() throws Exception {
		Payment p = qrPayment("p1", "org-1", "booking-1", "duty-1");
		stubOutstanding(List.of(p));

		job.reconcileOutstandingQrPayments();
		now.set(now.get().plusSeconds(3)); // still well inside the initial 7s window
		job.reconcileOutstandingQrPayments();

		verify(razorpayPaymentService, times(1)).isPaidByQR("org-1", "booking-1", "duty-1");
	}

	@Test
	void tick_afterIntervalElapses_rechecksAndDoublesTheInterval() throws Exception {
		Payment p = qrPayment("p1", "org-1", "booking-1", "duty-1");
		stubOutstanding(List.of(p));

		job.reconcileOutstandingQrPayments(); // check #1 (t=0), next due at t=7s

		now.set(now.get().plusSeconds(7));
		job.reconcileOutstandingQrPayments(); // check #2 (t=7s), next due at t=7+14=21s

		now.set(now.get().plusSeconds(13)); // t=20s -- not yet due
		job.reconcileOutstandingQrPayments();
		verify(razorpayPaymentService, times(2)).isPaidByQR("org-1", "booking-1", "duty-1");

		now.set(now.get().plusSeconds(2)); // t=22s -- now past the 21s mark
		job.reconcileOutstandingQrPayments(); // check #3

		verify(razorpayPaymentService, times(3)).isPaidByQR("org-1", "booking-1", "duty-1");
	}

	@Test
	void backoff_neverExceedsTheConfiguredMaximumInterval() throws Exception {
		Payment p = qrPayment("p1", "org-1", "booking-1", "duty-1");
		stubOutstanding(List.of(p));

		// Drive the interval well past the point it would keep doubling
		// (7 -> 14 -> 28 -> 56 -> 112 -> capped at 120) with a generous
		// number of ticks, jumping the clock further ahead each time.
		int checks = 1;
		job.reconcileOutstandingQrPayments();
		for (int i = 0; i < 8; i++) {
			now.set(now.get().plusSeconds(130)); // always >= the 120s cap
			job.reconcileOutstandingQrPayments();
			checks++;
		}

		verify(razorpayPaymentService, times(checks)).isPaidByQR("org-1", "booking-1", "duty-1");

		// One more tick only 100s later (less than the 120s cap) must NOT
		// trigger a recheck -- proves the interval is capped at 120s, not
		// left to keep doubling indefinitely.
		now.set(now.get().plusSeconds(100));
		job.reconcileOutstandingQrPayments();
		verify(razorpayPaymentService, times(checks)).isPaidByQR("org-1", "booking-1", "duty-1");
	}

	@Test
	void paymentNoLongerOutstanding_isNeverCheckedAgain() throws Exception {
		Payment p = qrPayment("p1", "org-1", "booking-1", "duty-1");
		stubOutstanding(List.of(p));
		job.reconcileOutstandingQrPayments();
		verify(razorpayPaymentService, times(1)).isPaidByQR("org-1", "booking-1", "duty-1");

		// Simulates the payment leaving PaymentStatus.INITIATED -- either
		// because the webhook already confirmed it, or its QR expired --
		// either way it stops matching the repository query.
		stubOutstanding(List.of());
		now.set(now.get().plusSeconds(200));
		job.reconcileOutstandingQrPayments();

		verify(razorpayPaymentService, times(1)).isPaidByQR("org-1", "booking-1", "duty-1");
	}

	@Test
	void transientProviderError_stillAdvancesBackoff_insteadOfTightRetrying() throws Exception {
		Payment p = qrPayment("p1", "org-1", "booking-1", "duty-1");
		stubOutstanding(List.of(p));
		doThrow(new RuntimeException("Razorpay timeout")).when(razorpayPaymentService).isPaidByQR("org-1", "booking-1", "duty-1");

		assertDoesNotThrow(job::reconcileOutstandingQrPayments);

		now.set(now.get().plusSeconds(3)); // still inside the post-failure backoff window
		job.reconcileOutstandingQrPayments();

		verify(razorpayPaymentService, times(1)).isPaidByQR("org-1", "booking-1", "duty-1");
	}

	@Test
	void concurrentDuties_backOffIndependently() throws Exception {
		Payment a = qrPayment("p1", "org-1", "booking-1", "duty-1");
		stubOutstanding(List.of(a));
		job.reconcileOutstandingQrPayments(); // a's next check due at t=7s

		now.set(now.get().plusSeconds(3)); // t=3s -- a not yet due
		Payment b = qrPayment("p2", "org-1", "booking-2", "duty-2");
		stubOutstanding(List.of(a, b));
		job.reconcileOutstandingQrPayments(); // b is brand new -- must still be checked immediately

		verify(razorpayPaymentService, times(1)).isPaidByQR("org-1", "booking-1", "duty-1");
		verify(razorpayPaymentService, times(1)).isPaidByQR("org-1", "booking-2", "duty-2");
	}
}

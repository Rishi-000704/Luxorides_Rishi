package com.core.services;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Payment;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

/*
 * Replaces the driver app's payment-status poll as the only thing that ever
 * asks Razorpay whether a driver-duty QR was paid. RazorpayPaymentService's
 * isPaidByQR/confirmQrPayment (and the PaymentConfirmedEvent they publish)
 * were previously only reachable from that poll endpoint -- removing the
 * client poll without this job would leave real QR payments unconfirmed
 * forever, since nothing else ever calls Razorpay's status API.
 *
 * MOCK-gateway QR payments are auto-confirmed at creation time (see
 * MockPaymentService.generateMockQr) so they never appear in this query --
 * this job only ever touches real RAZORPAY payments still INITIATED.
 */
@Component
@Slf4j
public class DutyPaymentReconciliationJob {

	private static final String DRIVER_DUTY_QR_CONTEXT = "DRIVER_DUTY_QR";

	/*
	 * P1.10 -- this job previously called Razorpay's status API for every
	 * outstanding QR on every 7s tick, regardless of how long it had already
	 * been pending. That's negligible for one duty; at real concurrent-driver
	 * volume it becomes real, recurring per-payment API cost. Webhook
	 * confirmation remains the primary, near-instant path (see
	 * RazorpayWebhookService) -- this job is only the fallback for a missed
	 * or delayed webhook, so it doesn't need to check every pending payment
	 * every 7 seconds to stay useful. Per-payment exponential backoff keeps
	 * the fast initial check (a webhook miss is still caught within ~7s,
	 * same as before) while cutting steady-state traffic for anything
	 * that's been pending a while, capped so reconciliation never goes
	 * silent for long even in the worst case -- the QR itself stays valid
	 * for 115 minutes (see RazorpayPaymentService#generateQrCode), so a
	 * worst-case 120s check interval still reconciles well within that
	 * window.
	 */
	private static final long INITIAL_INTERVAL_SECONDS = 7L;
	private static final long MAX_INTERVAL_SECONDS = 120L;

	private final PaymentRepository paymentRepository;
	private final RazorpayPaymentService razorpayPaymentService;
	private final Clock clock;

	@Autowired
	public DutyPaymentReconciliationJob(PaymentRepository paymentRepository, RazorpayPaymentService razorpayPaymentService) {
		this(paymentRepository, razorpayPaymentService, Clock.systemUTC());
	}

	// Test-only seam so the backoff schedule can be verified deterministically
	// instead of via real sleeps.
	DutyPaymentReconciliationJob(PaymentRepository paymentRepository, RazorpayPaymentService razorpayPaymentService, Clock clock) {
		this.paymentRepository = paymentRepository;
		this.razorpayPaymentService = razorpayPaymentService;
		this.clock = clock;
	}

	/*
	 * In-memory, per-payment backoff state -- deliberately not persisted.
	 * This job is a best-effort safety net (webhook is authoritative), so a
	 * backend restart simply resets any in-flight backoff back to the fast
	 * initial interval for whatever is still outstanding at that moment:
	 * safe and self-correcting, not worth a schema change for. Bounded by
	 * construction -- an entry only exists for a currently-outstanding
	 * payment and is pruned the same tick it stops being outstanding
	 * (paid via webhook, or expired), so this can never grow unbounded.
	 */
	private final ConcurrentHashMap<String, ReconciliationState> backoffState = new ConcurrentHashMap<>();

	@Scheduled(fixedDelay = 7000)
	public void reconcileOutstandingQrPayments() {
		Instant now = Instant.now(clock);

		List<Payment> outstanding = paymentRepository
				.findAllByCollectionContextAndStatusAndGatewayAndExpiresAtAfter(
						DRIVER_DUTY_QR_CONTEXT, PaymentStatus.INITIATED, PaymentGateway.RAZORPAY, now);

		Set<String> outstandingIds = outstanding.stream().map(Payment::getId).collect(Collectors.toSet());
		backoffState.keySet().retainAll(outstandingIds);

		for (Payment payment : outstanding) {
			ReconciliationState state = backoffState.get(payment.getId());

			// Not yet due for its next check -- skip this tick's Razorpay call
			// for this payment specifically; every other outstanding payment
			// is still evaluated independently on its own schedule.
			if (state != null && now.isBefore(state.nextCheckAt())) {
				continue;
			}

			try {
				razorpayPaymentService.isPaidByQR(
						payment.getOrgId(),
						payment.getBooking().getBookingId(),
						payment.getCollectionContextId());
			} catch (Exception ex) {
				// Transient provider errors still advance the backoff below --
				// a failing Razorpay call must not turn into a tight 7s retry
				// loop against a provider that's already erroring.
				log.warn("Duty payment reconciliation failed for payment {}: {}", payment.getId(), ex.getMessage());
			}

			long nextIntervalSeconds = state == null
					? INITIAL_INTERVAL_SECONDS
					: Math.min(state.intervalSeconds() * 2, MAX_INTERVAL_SECONDS);
			backoffState.put(payment.getId(), new ReconciliationState(now.plusSeconds(nextIntervalSeconds), nextIntervalSeconds));
		}
	}

	private record ReconciliationState(Instant nextCheckAt, long intervalSeconds) {
	}
}

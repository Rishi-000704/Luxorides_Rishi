package com.core.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Payment;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * Safety-net reconciliation for booking-checkout (ClientPaymentController ->
 * RazorpayPaymentService.createRazorpayOrder) payments -- the exact gap
 * identified in the payment recovery audit: a customer's browser can complete
 * Razorpay checkout and then never deliver the /verify call (closed tab, lost
 * network, crash), leaving the local Payment stuck INITIATED with no other
 * path to recovery. RazorpayWebhookService normally closes this within
 * seconds of Razorpay's own payment.captured event; this job exists for the
 * cases a webhook delivery is missed, delayed, or not yet configured for an
 * org.
 *
 * Deliberately a separate class from DutyPaymentReconciliationJob rather than
 * a generalization of it: that job's isPaidByQR call, 7-second cadence and
 * QR-specific identifiers (dutyId, gatewayQrCodeId) don't apply here, and
 * checkout recovery is not latency-sensitive the way a driver waiting on a
 * customer's QR scan is -- forcing the two into one method would make both
 * harder to reason about for no shared benefit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutPaymentReconciliationJob {

	/*
	 * Recovering a lost browser session is not time-critical the way a QR scan
	 * is; a few minutes' delay before a booking shows CONFIRMED is an
	 * acceptable trade against Razorpay API traffic (orders.fetch +
	 * orders.fetchPayments per outstanding payment per run). The webhook is
	 * what gives near-instant recovery for the common case -- this is its
	 * bounded-cost safety net, not the primary mechanism.
	 */
	private static final long POLL_INTERVAL_MS = 5 * 60 * 1000L;

	// Bounds the query itself so genuinely ancient, long-abandoned INITIATED
	// rows are never scanned forever, independent of the per-payment expiry
	// grace period below.
	private static final Duration LOOKBACK_WINDOW = Duration.ofHours(48);

	// How long past a payment's own expiresAt (30 minutes -- see
	// RazorpayPaymentService.CHECKOUT_ORDER_REUSE_MINUTES) to keep retrying
	// before marking it FAILED and no longer polling it. Generous on purpose:
	// this only stops *polling*, it does not forfeit recovery -- a late
	// webhook can still confirm a FAILED payment (see
	// RazorpayPaymentService.reconcileByGatewayOrderId).
	private static final Duration EXPIRY_GRACE_PERIOD = Duration.ofHours(1);

	private final PaymentRepository paymentRepository;
	private final RazorpayPaymentService razorpayPaymentService;

	@Scheduled(fixedDelay = POLL_INTERVAL_MS)
	public void reconcileOutstandingCheckoutPayments() {
		Instant lookbackSince = Instant.now().minus(LOOKBACK_WINDOW);

		List<Payment> outstanding = paymentRepository
				.findAllByGatewayAndStatusAndBookingIsNotNullAndCollectionContextIsNullAndCreatedAtAfter(
						PaymentGateway.RAZORPAY, PaymentStatus.INITIATED, lookbackSince);

		Instant graceDeadline = Instant.now().minus(EXPIRY_GRACE_PERIOD);

		for (Payment payment : outstanding) {
			String gatewayOrderId = payment.getGatewayOrderId();

			if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
				continue;
			}

			try {
				razorpayPaymentService.reconcileByGatewayOrderId(payment.getOrgId(), gatewayOrderId);
			} catch (Exception ex) {
				log.warn("Checkout payment reconciliation failed for payment {}: {}", payment.getId(), ex.getMessage());
				continue;
			}

			try {
				razorpayPaymentService.expireIfStillInitiatedPastGracePeriod(gatewayOrderId, graceDeadline);
			} catch (Exception ex) {
				log.warn("Checkout payment expiry check failed for payment {}: {}", payment.getId(), ex.getMessage());
			}
		}
	}
}

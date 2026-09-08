package com.core.gateway.razerpay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.core.events.PaymentConfirmedEvent;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.Booking;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentMode;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingRepository;
import com.core.repositories.PaymentRepository;
import com.core.services.ClientBookingService;
import com.core.util.PaymentUtil;
import com.razorpay.Order;
import com.razorpay.QrCode;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RazorpayPaymentService {

	private final RazorpayClientFactory razorpayClientFactory;
	private final BookingRepository bookingRepo;
	private final PaymentRepository paymentRepo;
	private final ClientBookingService clientBookingService;
	private final ApplicationEventPublisher eventPublisher;
	private final PaymentEventAssembler paymentEventAssembler;

	private static final String DRIVER_DUTY_QR_CONTEXT = "DRIVER_DUTY_QR";

	/*
	 * How long a client-app checkout order is considered reusable for. Not a
	 * Razorpay-imposed value (Razorpay orders don't expire on their own) --
	 * this bounds how long a stale INITIATED order can be silently handed
	 * back to a customer who re-opens checkout, so a very old abandoned
	 * attempt doesn't get resurrected indefinitely. Chosen conservatively
	 * for a single checkout-modal session; revisit if product wants a
	 * different window.
	 */
	private static final long CHECKOUT_ORDER_REUSE_MINUTES = 30;

	/*
	 * P1.7 -- explicit connect/request timeouts. This client backs
	 * fetchPaymentsForQr, the one Razorpay call DutyPaymentReconciliationJob's
	 * every-7-second loop makes; without a bound, a single hung request could
	 * stall that whole scheduled run (and, since it's fixedDelay, push every
	 * other outstanding QR payment's reconciliation back with it) instead of
	 * failing fast into the loop's existing per-payment catch-and-log.
	 */
	private static final Duration RAZORPAY_HTTP_TIMEOUT = Duration.ofSeconds(10);

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(RAZORPAY_HTTP_TIMEOUT)
			.build();

	/* ================= PUBLIC KEY ================= */

	public String getPublicKey(String orgId) {
		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		return credentials.keyId();
	}

	/* ================= CREATE ORDER + CHECKOUT PAYLOAD ================= */

	public RazorpayCheckoutPayload createRazorpayOrder(String bookingId, String clientId, String orgId) throws Exception {

		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		razorpayClientFactory.assertCheckoutEnabled(credentials);

		RazorpayClient razorpayClient = razorpayClientFactory.client(credentials);

		/*
		 * Lock the booking row for the duration of this transaction -- this is
		 * what makes concurrent createRazorpayOrder calls for the same booking
		 * safe: a second request blocks here until the first one's INSERT of
		 * the new Payment row (below) has committed and released the lock, at
		 * which point the second request's own read of existing payments will
		 * see it and can reuse it instead of creating a duplicate order. Same
		 * lock-the-aggregate-root pattern EstimatePaymentSettlementService
		 * already uses for the estimate-payment flow.
		 */
		Booking booking = bookingRepo.lockByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new IllegalStateException("Booking not found"));

		/*
		 * P0 IDOR fix -- must be checked before ANY Razorpay call (the reuse
		 * path's orders.fetch below included), and before this booking's
		 * client name/phone/email are ever read into a checkout payload.
		 * Without this, a customer could create/reuse a live payment order --
		 * and see another customer's PII -- for a bookingId they don't own.
		 */
		if (!clientId.equals(booking.getClientId())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "This booking does not belong to you");
		}

		Money pending = calculatePendingAmount(booking);

		if (pending == null || pending.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalStateException("No pending amount to pay");
		}

		Payment reusable = paymentRepo
				.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
						orgId, bookingId, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED)
				.filter(p -> p.getExpiresAt() != null && p.getExpiresAt().isAfter(Instant.now()))
				.filter(p -> p.getReceivedAmount() != null
						&& p.getReceivedAmount().getAmount().compareTo(pending.getAmount()) == 0)
				.filter(p -> p.getGatewayOrderId() != null && !p.getGatewayOrderId().isBlank())
				.orElse(null);

		if (reusable != null) {

			/*
			 * P1B/1D -- the local INITIATED status alone is not proof this order
			 * is still safe to reuse: if Razorpay already captured it and our own
			 * /verify call for it was lost (the exact recovery scenario this
			 * change exists for), handing the SAME order id back to Razorpay
			 * Checkout would offer to pay an already-paid order again. One extra
			 * live lookup here -- order creation is not a hot path -- mirrors
			 * PublicEstimateService's existing equivalent check for the Estimate
			 * flow. Never create a second order while Razorpay's state can't be
			 * established either: that would risk a duplicate charge.
			 */
			Order existingOrder;

			try {
				existingOrder = razorpayClient.orders.fetch(reusable.getGatewayOrderId());
			} catch (Exception exception) {
				throw new IllegalStateException(
						"Unable to verify the existing Razorpay order before reuse. Please retry shortly.", exception);
			}

			/*
			 * Explicit <String> witness, not String.valueOf(existingOrder.get(...)):
			 * get() is generic (<T> T get(String)), and String.valueOf is
			 * overloaded with a char[] variant -- without a witness, javac can
			 * resolve the more specific valueOf(char[]) overload and infer
			 * T=char[] for get(), which then throws a ClassCastException at
			 * runtime casting the real String value to char[].
			 */
			String orderStatusRaw = existingOrder.<String>get("status");
			String orderStatus = orderStatusRaw == null
					? ""
					: orderStatusRaw.trim().toLowerCase(java.util.Locale.ROOT);

			if ("paid".equals(orderStatus)) {
				/*
				 * Do not reconcile inline here: this method is already inside the
				 * booking-row transaction and about to throw, which would roll
				 * back any confirmation written in the same transaction. The
				 * webhook and CheckoutPaymentReconciliationJob (running at most a
				 * few minutes behind) independently close this out shortly --
				 * this check's job is only to guarantee we never hand back an
				 * already-paid order, not to perform the recovery itself.
				 */
				throw new IllegalStateException(
						"Payment for this booking is already completed and is being confirmed. Please refresh shortly.");
			}

			if ("created".equals(orderStatus) || "attempted".equals(orderStatus)) {

				String clientNameReuse = booking.getClient() != null && booking.getClient().getName() != null
						? booking.getClient().getName().getDisplayName()
						: null;
				String clientPhoneReuse = booking.getClient() != null ? booking.getClient().getPhone() : null;
				String clientEmailReuse = booking.getClient() != null ? booking.getClient().getEmail() : null;

				long reusedAmountInPaise = reusable.getReceivedAmount().getAmount()
						.setScale(2, RoundingMode.HALF_UP)
						.multiply(BigDecimal.valueOf(100))
						.longValueExact();

				return RazorpayCheckoutPayload.builder()
						.key(credentials.keyId())
						.orderId(reusable.getGatewayOrderId())
						.amount(reusedAmountInPaise)
						.currency(credentials.resolvedCurrency())
						.prefill(RazorpayCheckoutPayload.Prefill.builder()
								.name(clientNameReuse)
								.contact(clientPhoneReuse)
								.email(clientEmailReuse)
								.build())
						.build();
			}

			/*
			 * Any other Razorpay order status is not safely reusable -- fall
			 * through to creating a fresh order below, same as if no local
			 * reuse candidate had been found at all.
			 */
		}

		long amountInPaise = pending.getAmount()
				.setScale(2, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.longValueExact();

		JSONObject options = new JSONObject();
		options.put("amount", amountInPaise);
		options.put("currency", credentials.resolvedCurrency());
		options.put("receipt", booking.getBookingId());

		JSONObject notes = new JSONObject();
		notes.put("context", "CLIENT_APP_BOOKING_PAYMENT");
		notes.put("orgId", orgId);
		notes.put("bookingId", booking.getBookingId());
		options.put("notes", notes);

		Order order = razorpayClient.orders.create(options);

		Payment payment = new Payment();
		payment.setOrgId(orgId);
		payment.setPaymentMode(PaymentMode.UNKNOWN);
		payment.setBooking(booking);
		payment.setGateway(PaymentGateway.RAZORPAY);
		payment.setGatewayOrderId(order.get("id"));
		payment.setReceivedAmount(pending);
		payment.setTds(Money.INR(BigDecimal.ZERO));
		payment.setStatus(PaymentStatus.INITIATED);
		payment.setCreatedAt(Instant.now());
		payment.setExpiresAt(Instant.now().plus(CHECKOUT_ORDER_REUSE_MINUTES, ChronoUnit.MINUTES));

		paymentRepo.save(payment);

		String clientName = booking.getClient() != null && booking.getClient().getName() != null
				? booking.getClient().getName().getDisplayName()
				: null;

		String clientPhone = booking.getClient() != null
				? booking.getClient().getPhone()
				: null;

		String clientEmail = booking.getClient() != null
				? booking.getClient().getEmail()
				: null;

		return RazorpayCheckoutPayload.builder()
				.key(credentials.keyId())
				.orderId(order.get("id"))
				.amount(amountInPaise)
				.currency(credentials.resolvedCurrency())
				.prefill(RazorpayCheckoutPayload.Prefill.builder()
						.name(clientName)
						.contact(clientPhone)
						.email(clientEmail)
						.build())
				.build();
	}

	/* ================= VERIFY PAYMENT ================= */

	public void verifyPayment(
			String orgId,
			String bookingId,
			String orderId,
			String paymentId,
			String signature
	) throws Exception {

		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		razorpayClientFactory.assertCheckoutEnabled(credentials);

		RazorpayClient razorpayClient = razorpayClientFactory.client(credentials);

		/*
		 * PESSIMISTIC_WRITE on the payment row itself (not the booking -- the
		 * risk here is two concurrent verify calls racing on the SAME order,
		 * e.g. a double-tap after Razorpay's checkout callback fires, not two
		 * different orders for the same booking). Serializes any concurrent
		 * verify against this exact orderId.
		 */
		Payment payment = paymentRepo.lockByGatewayOrderId(orderId)
				.orElseThrow(() -> new IllegalStateException("Payment not found"));

		if (!orgId.equals(payment.getOrgId())) {
			throw new SecurityException("Payment organization mismatch");
		}

		Booking booking = payment.getBooking();

		if (booking == null || !booking.getBookingId().equals(bookingId)) {
			throw new IllegalStateException("Booking mismatch");
		}

		/*
		 * Make verification idempotent -- same guard EstimatePaymentSettlementService
		 * already uses. A repeated (already-successful) verify call for a payment
		 * that's already CONFIRMED is a safe no-op, not a failure: no re-fetch from
		 * Razorpay, no re-publish of PaymentConfirmedEvent, no repeated downstream
		 * side effects. The controller returns the same SUCCESS response either way.
		 */
		if (payment.getStatus() == PaymentStatus.CONFIRMED) {
			return;
		}

		razorpayClientFactory.verifySignature(credentials, orderId, paymentId, signature);

		/*
		 * Prevent one Razorpay payment id from being attached to a different
		 * Fleetovo payment record than the one it actually belongs to -- same
		 * protection EstimatePaymentSettlementService already has.
		 */
		if (paymentRepo.existsByGatewayPaymentIdAndIdNot(paymentId, payment.getId())) {
			throw new IllegalStateException("Razorpay payment is already linked to another payment record");
		}

		com.razorpay.Payment razorpayPayment = razorpayClient.payments.fetch(paymentId);

		String method = razorpayPayment.get("method");

		applyConfirmationAndNotify(
				payment, booking, orgId, bookingId,
				paymentId, signature, PaymentUtil.mapPaymentMode(method), Instant.now());
	}

	/*
	 * Shared tail for every path that turns a locked, INITIATED (or previously
	 * FAILED -- see reconcileByGatewayOrderId) Payment into CONFIRMED: sets the
	 * gateway fields, saves, publishes PaymentConfirmedEvent and, if the
	 * booking is still DRAFT, confirms it. Used by verifyPayment (browser,
	 * signature-verified) and reconcileByGatewayOrderId (webhook/scheduled recovery,
	 * provider-verified) so both paths behave identically once a payment is
	 * proven captured -- there is exactly one place this transition happens.
	 */
	private void applyConfirmationAndNotify(
			Payment payment,
			Booking booking,
			String orgId,
			String bookingId,
			String gatewayPaymentId,
			String signature,
			PaymentMode paymentMode,
			Instant transactionDate
	) {
		payment.setGatewayPaymentId(gatewayPaymentId);
		payment.setGatewaySignature(signature);
		payment.setTransactionNumber(gatewayPaymentId);
		payment.setTransactionDate(transactionDate);
		payment.setPaymentMode(paymentMode);
		payment.setStatus(PaymentStatus.CONFIRMED);

		Payment saved = paymentRepo.save(payment);

		PaymentConfirmedEvent event = paymentEventAssembler.toPaymentConfirmedEvent(booking, saved);
		eventPublisher.publishEvent(event);

		if (booking.getStatus() == BookingStatus.DRAFT) {
			clientBookingService.confirmBooking(orgId, bookingId);
		}
	}

	/* ================= RECOVERY (webhook + scheduled reconciliation) ================= */

	/*
	 * Public entry point for RazorpayWebhookService and
	 * CheckoutPaymentReconciliationJob: given an orderId we already created
	 * and stored locally, independently ask Razorpay for that order's current
	 * state and payments, and confirm the matching local Payment only if the
	 * provider genuinely proves it -- never because a webhook payload or a
	 * bare payment id said so.
	 *
	 * orgId must be resolved by the CALLER from a trustworthy source (the
	 * local Payment row for the job; the webhook URL path -- fixed dashboard
	 * configuration, not request payload -- for the webhook). It is checked
	 * against the payment's own stored orgId BEFORE any Razorpay API call is
	 * made using it: an org can never use its own (correctly-signed) webhook
	 * to trigger a live provider lookup, let alone a confirmation, for an
	 * orderId that resolves to a different org's payment.
	 */
	public void reconcileByGatewayOrderId(String orgId, String gatewayOrderId) throws Exception {

		/*
		 * PESSIMISTIC_WRITE on the payment row -- identical lock verifyPayment
		 * takes, so a browser /verify call, a webhook delivery (including a
		 * duplicate/retried delivery) and this scheduled job can never confirm
		 * the same order twice: whichever transaction gets the lock first
		 * commits CONFIRMED, and every other one then sees CONFIRMED under the
		 * idempotency check below and safely no-ops. This is a real database
		 * row lock, so it serializes correctly across multiple application
		 * instances too -- no separate distributed lock is needed.
		 */
		Payment payment = paymentRepo.lockByGatewayOrderId(gatewayOrderId).orElse(null);

		if (payment == null) {
			log.warn("Razorpay reconciliation: no local payment found for order {}", gatewayOrderId);
			return;
		}

		if (!orgId.equals(payment.getOrgId())) {
			throw new SecurityException("Payment organization mismatch");
		}

		if (payment.getStatus() == PaymentStatus.CONFIRMED) {
			return;
		}

		/*
		 * Only ever recover an order that is still open (INITIATED) or one our
		 * own bounded reconciliation previously gave up on (FAILED -- see
		 * CheckoutPaymentReconciliationJob's expiry handling). A late webhook
		 * for a payment we already marked FAILED after its grace window is
		 * exactly the case that mechanism exists for. Never touch REFUNDED or
		 * CANCELLED -- those are explicit, immutable outcomes.
		 */
		if (payment.getStatus() != PaymentStatus.INITIATED && payment.getStatus() != PaymentStatus.FAILED) {
			log.warn("Razorpay reconciliation: payment {} for order {} is in terminal status {} -- not recoverable",
					payment.getId(), gatewayOrderId, payment.getStatus());
			return;
		}

		Booking booking = payment.getBooking();

		if (booking == null) {
			log.warn("Razorpay reconciliation: payment {} for order {} has no booking -- skipping", payment.getId(), gatewayOrderId);
			return;
		}

		long expectedAmount = toSubunits(payment.getReceivedAmount().getAmount());
		String expectedCurrency = payment.getReceivedAmount().getCurrency().name();

		/*
		 * Only now -- after confirming this order genuinely belongs to orgId --
		 * do we resolve that org's credentials and call out to Razorpay. This
		 * ordering matters: a caller could otherwise cause a live provider call
		 * to be made with one org's credentials for an orderId that turns out to
		 * belong to a different org, before the mismatch is ever detected.
		 */
		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		RazorpayClient razorpayClient = razorpayClientFactory.client(credentials);

		Order order = razorpayClient.orders.fetch(gatewayOrderId);

		RazorpayPaymentValidation.validateOrderIdentity(order, gatewayOrderId, expectedAmount, expectedCurrency);

		List<com.razorpay.Payment> candidatePayments = razorpayClient.orders.fetchPayments(gatewayOrderId);

		for (com.razorpay.Payment candidate : candidatePayments) {

			if (!RazorpayPaymentValidation.isCaptured(candidate)) {
				continue;
			}

			try {
				RazorpayPaymentValidation.validatePaymentIdentity(candidate, gatewayOrderId, expectedAmount, expectedCurrency);
			} catch (IllegalStateException mismatch) {
				log.warn("Razorpay reconciliation: candidate payment on order {} failed identity validation: {}",
						gatewayOrderId, mismatch.getMessage());
				continue;
			}

			String candidatePaymentId = candidate.<String>get("id");

			if (candidatePaymentId == null || candidatePaymentId.isBlank()) {
				continue;
			}

			if (paymentRepo.existsByGatewayPaymentIdAndIdNot(candidatePaymentId, payment.getId())) {
				log.warn("Razorpay reconciliation: payment {} is already linked to a different local payment -- refusing to confirm {}",
						candidatePaymentId, payment.getId());
				continue;
			}

			String method = candidate.get("method");
			Object createdAtRaw = candidate.get("created_at");
			Instant transactionDate = createdAtRaw instanceof Number number
					? Instant.ofEpochSecond(number.longValue())
					: Instant.now();

			applyConfirmationAndNotify(
					payment, booking, orgId, booking.getBookingId(),
					candidatePaymentId, null, PaymentUtil.mapPaymentMode(method), transactionDate);

			log.info("Razorpay reconciliation: recovered payment {} (order {}) via provider lookup", payment.getId(), gatewayOrderId);
			return;
		}

		log.info("Razorpay reconciliation: no captured payment found yet for order {} (payment {})", gatewayOrderId, payment.getId());
	}

	/*
	 * Called by CheckoutPaymentReconciliationJob after a reconcileByGatewayOrderId
	 * attempt found nothing captured -- stops the job from polling a genuinely
	 * abandoned checkout attempt forever. FAILED here is not permanent: a late
	 * webhook delivery can still recover it (see reconcileByGatewayOrderId, which
	 * accepts FAILED as a recoverable starting status). Re-locks fresh rather
     * than trusting an in-memory Payment the caller already holds, since a
	 * concurrent confirmation may have just happened.
	 */
	public void expireIfStillInitiatedPastGracePeriod(String gatewayOrderId, Instant graceDeadline) {
		Payment payment = paymentRepo.lockByGatewayOrderId(gatewayOrderId).orElse(null);

		if (payment == null || payment.getStatus() != PaymentStatus.INITIATED) {
			return;
		}

		if (payment.getExpiresAt() == null || !payment.getExpiresAt().isBefore(graceDeadline)) {
			return;
		}

		payment.setStatus(PaymentStatus.FAILED);
		payment.setRemarks("No captured Razorpay payment found for this order within the recovery window");
		paymentRepo.save(payment);

		log.info("Checkout payment {} (order {}) marked FAILED after expiry grace period with no captured payment found",
				payment.getId(), gatewayOrderId);
	}

	private long toSubunits(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).longValueExact();
	}

	/* ================= REFUND (admin-approved only -- see RefundRequestService) ================= */

	/*
	 * Real first-ever use of the SDK's refund API in this codebase (confirmed
	 * by prior research -- razorpayClient.payments.refund was never called
	 * anywhere before this). Only ever invoked from RefundRequestService.approve,
	 * itself only reachable via an employee explicitly approving a
	 * PENDING_REVIEW RefundRequest -- never automatically on cancellation.
	 */
	public String refundPayment(String orgId, String bookingId, BigDecimal refundAmount) throws Exception {

		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		RazorpayClient razorpayClient = razorpayClientFactory.client(credentials);

		Booking booking = bookingRepo.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new IllegalStateException("Booking not found"));

		Payment confirmedPayment = booking.getPayments().stream()
				.filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
				.filter(p -> p.getGatewayPaymentId() != null && !p.getGatewayPaymentId().isBlank())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"No confirmed gateway payment found on this booking to refund"));

		long amountInPaise = refundAmount
				.setScale(2, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.longValueExact();

		JSONObject refundRequest = new JSONObject();
		refundRequest.put("amount", amountInPaise);

		JSONObject notes = new JSONObject();
		notes.put("source", "FLEETOVO_REFUND_REQUEST");
		notes.put("orgId", orgId);
		notes.put("bookingId", bookingId);
		refundRequest.put("notes", notes);

		/*
		 * P1H -- provider-side idempotency check before issuing a new refund.
		 * If a prior approve() attempt already refunded this exact amount at
		 * Razorpay but failed to persist that locally (see the catch block
		 * below), a retry must find and reuse that refund rather than issuing a
		 * second real one. Financial correctness is worth the extra API call
		 * here even though this path is invoked far less often than checkout.
		 */
		String refundId = findExistingRefund(razorpayClient, confirmedPayment.getGatewayPaymentId(), amountInPaise)
				.orElse(null);

		if (refundId != null) {
			log.warn("Reusing existing Razorpay refund {} for payment {} instead of issuing a duplicate",
					refundId, confirmedPayment.getId());
		} else {
			Refund refund = razorpayClient.payments.refund(
					confirmedPayment.getGatewayPaymentId(), refundRequest);

			refundId = refund.get("id");
		}

		try {
			confirmedPayment.setStatus(PaymentStatus.REFUNDED);
			paymentRepo.save(confirmedPayment);
		} catch (Exception persistenceFailure) {
			/*
			 * The Razorpay refund itself is done -- real money has moved. Never
			 * swallow that fact into a generic failure a human could read as
			 * "nothing happened, safe to try another way"; surface the refund id
			 * so RefundRequestService.approve can record it even while marking
			 * the request as needing verification, not simply FAILED.
			 */
			log.error("Razorpay refund {} succeeded for payment {} but local persistence failed -- "
					+ "manual reconciliation required", refundId, confirmedPayment.getId(), persistenceFailure);
			throw new RefundPersistenceException(refundId, persistenceFailure);
		}

		return refundId;
	}

	/*
	 * Ops recovery (RefundRequestService.verifyRecovery): given a
	 * COMPLETED_NEEDS_VERIFICATION request, prove -- from Razorpay directly,
	 * never from local state alone -- whether the refund already exists
	 * before an operator is allowed to treat it as safe to retry.
	 *
	 * If a gatewayRefundId is already on the local record (the normal case:
	 * refundPayment's catch block always captures it from Razorpay's own
	 * response before local persistence failed), a single targeted
	 * payments.fetchRefund confirms that exact id still checks out against
	 * this payment/amount -- cheaper and more certain than a broader search.
	 * Only falls back to the amount-based fetchAllRefunds search
	 * (findExistingRefund, same mechanism refundPayment's own pre-refund
	 * idempotency check uses) when there is no known id or the targeted
	 * lookup could not confirm it -- never as the default path, to avoid an
	 * unnecessary provider call when local state already narrows the answer.
	 */
	public record RefundVerification(String refundId) {
	}

	public RefundVerification verifyRefund(
			String orgId,
			String bookingId,
			String knownRefundId,
			BigDecimal expectedRefundAmount
	) throws Exception {

		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		RazorpayClient razorpayClient = razorpayClientFactory.client(credentials);

		Booking booking = bookingRepo.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new IllegalStateException("Booking not found"));

		Payment gatewayPayment = booking.getPayments().stream()
				.filter(p -> p.getGatewayPaymentId() != null && !p.getGatewayPaymentId().isBlank())
				.filter(p -> p.getStatus() == PaymentStatus.CONFIRMED || p.getStatus() == PaymentStatus.REFUNDED)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"No confirmed/refunded gateway payment found on this booking to verify"));

		long expectedAmountInPaise = toSubunits(expectedRefundAmount);

		if (knownRefundId != null && !knownRefundId.isBlank()) {
			try {
				Refund refund = razorpayClient.payments.fetchRefund(gatewayPayment.getGatewayPaymentId(), knownRefundId);

				if (refund != null) {
					Object amount = refund.get("amount");

					if (amount instanceof Number number && number.longValue() == expectedAmountInPaise) {
						markPaymentRefundedIfNeeded(gatewayPayment);
						return new RefundVerification(knownRefundId);
					}

					log.warn("Known refund {} for payment {} did not match expected amount on re-verification -- "
							+ "falling back to a broader search",
							knownRefundId, gatewayPayment.getId());
				}
			} catch (Exception ex) {
				log.warn("Targeted refund lookup failed for refund {}, falling back to a broader search: {}",
						knownRefundId, ex.getMessage());
			}
		}

		Optional<String> found = findExistingRefund(razorpayClient, gatewayPayment.getGatewayPaymentId(), expectedAmountInPaise);

		found.ifPresent(id -> markPaymentRefundedIfNeeded(gatewayPayment));

		return new RefundVerification(found.orElse(null));
	}

	/*
	 * refundPayment's local Payment.setStatus(REFUNDED) is rolled back
	 * whenever RefundPersistenceException fires (same transaction, same
	 * failed save) -- so a payment genuinely refunded at Razorpay can still
	 * read CONFIRMED locally until this reconciles it. Only ever called after
	 * a provider lookup has just proven the refund exists.
	 */
	private void markPaymentRefundedIfNeeded(Payment payment) {
		if (payment.getStatus() == PaymentStatus.REFUNDED) {
			return;
		}

		payment.setStatus(PaymentStatus.REFUNDED);
		paymentRepo.save(payment);
	}

	/*
	 * Matches by gatewayPaymentId + exact refunded amount -- the strongest
	 * identifiers available without a client-generated idempotency key.
	 * RefundRequestService never issues more than one RefundRequest per
	 * cancellation and cancellation is a one-time transition (see
	 * BookingService.cancelBooking's CANCELLED guard), so a legitimate second,
	 * independently-intended refund of the identical amount on the same
	 * payment is not a realistic case this flow can produce today.
	 */
	private Optional<String> findExistingRefund(RazorpayClient razorpayClient, String gatewayPaymentId, long amountInPaise)
			throws Exception {

		List<Refund> existingRefunds = razorpayClient.payments.fetchAllRefunds(gatewayPaymentId);

		if (existingRefunds == null) {
			return Optional.empty();
		}

		for (Refund existing : existingRefunds) {
			Object amount = existing.get("amount");

			if (amount instanceof Number number && number.longValue() == amountInPaise) {
				String existingRefundId = existing.<String>get("id");
				return Optional.ofNullable(existingRefundId);
			}
		}

		return Optional.empty();
	}

	/* ================= AMOUNT ================= */

	private Money calculatePendingAmount(Booking booking) {

		BigDecimal total = booking.getTotal().getAmount();

		BigDecimal paid = booking.getPayments().stream()
				.filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
				.map(p -> p.getReceivedAmount().getAmount())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal pending = total.subtract(paid);

		if (pending.compareTo(BigDecimal.ZERO) < 0) {
			pending = BigDecimal.ZERO;
		}

		return Money.INR(pending);
	}

	/* ================= QR CODE FOR DRIVER LINK ================= */

	/*
	 * REQUIRES_NEW rather than the class-default REQUIRED: this is called from inside
	 * ExternalDriverDutyService.submitEnd's own @Transactional method, which already
	 * treats QR-generation failure as non-fatal (catches the exception and returns a
	 * QR_GENERATION_FAILED instruction). With the default REQUIRED propagation this
	 * method joins that same physical transaction, so when it throws (e.g. Razorpay not
	 * configured for the org), Spring's transaction interceptor marks the *shared*
	 * transaction rollback-only before the caller's catch block ever runs -- the caller's
	 * graceful handling then has no effect, and the whole duty completion (checkpoint,
	 * odometer, fare) silently rolls back with UnexpectedRollbackException instead of the
	 * real error. REQUIRES_NEW isolates a failed QR attempt to its own transaction so the
	 * already-valid duty-completion work can still commit.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public RazorpayQrPayload generateQR(
			String orgId,
			String bookingId,
			String dutyId,
			BigDecimal amount
	) throws Exception {

		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		razorpayClientFactory.assertQrEnabled(credentials);

		RazorpayClient razorpayClient = razorpayClientFactory.client(credentials);

		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("QR amount must be greater than zero");
		}

		Booking booking = bookingRepo.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new IllegalStateException("Booking not found"));

		Payment existingPayment = paymentRepo
				.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						orgId,
						bookingId,
						DRIVER_DUTY_QR_CONTEXT,
						dutyId
				)
				.orElse(null);

		if (existingPayment != null
				&& existingPayment.getStatus() == PaymentStatus.INITIATED
				&& existingPayment.getExpiresAt() != null
				&& existingPayment.getExpiresAt().isAfter(Instant.now())
				&& existingPayment.getGatewayQrCodeId() != null
				&& !existingPayment.getGatewayQrCodeId().isBlank()) {

			return RazorpayQrPayload.builder()
					.paymentId(existingPayment.getId())
					.qrCodeId(existingPayment.getGatewayQrCodeId())
					.qrImageUrl(existingPayment.getGatewayQrImageUrl())
					.qrImageContent(existingPayment.getGatewayQrImageContent())
					.amount(existingPayment.getReceivedAmount().getAmount())
					.expiresAt(existingPayment.getExpiresAt())
					.build();
		}

		long amountInPaise = amount
				.setScale(2, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.longValueExact();

		long closeBy = Instant.now().plus(115, ChronoUnit.MINUTES).getEpochSecond();

		JSONObject qrRequest = new JSONObject();
		qrRequest.put("type", "upi_qr");
		qrRequest.put("name", credentials.resolvedMerchantName());
		qrRequest.put("usage", "single_use");
		qrRequest.put("fixed_amount", true);
		qrRequest.put("payment_amount", amountInPaise);
		qrRequest.put("description", "Fleetovo duty payment");
		qrRequest.put("close_by", closeBy);

		JSONObject notes = new JSONObject();
		notes.put("source", "FLEETOVO_DRIVER_DUTY");
		notes.put("orgId", orgId);
		notes.put("bookingId", bookingId);
		notes.put("dutyId", dutyId);
		qrRequest.put("notes", notes);

		QrCode qrCode = razorpayClient.qrCode.create(qrRequest);

		String qrCodeId = getRequiredString(qrCode, "id");
		String qrImageUrl = getOptionalString(qrCode, "image_url");
		String qrImageContent = getOptionalString(qrCode, "image_content");

		if ((qrImageContent == null || qrImageContent.isBlank())
				&& (qrImageUrl == null || qrImageUrl.isBlank())) {
			throw new IllegalStateException("Razorpay QR response has neither image_content nor image_url");
		}

		Payment payment = new Payment();
		payment.setOrgId(orgId);
		payment.setBooking(booking);
		payment.setPaymentMode(PaymentMode.UNKNOWN);
		payment.setGateway(PaymentGateway.RAZORPAY);
		payment.setReceivedAmount(Money.INR(amount));
		payment.setTds(Money.INR(BigDecimal.ZERO));
		payment.setStatus(PaymentStatus.INITIATED);

		payment.setGatewayQrCodeId(qrCodeId);
		payment.setGatewayQrImageUrl(qrImageUrl);
		payment.setGatewayQrImageContent(qrImageContent);

		payment.setCollectionContext(DRIVER_DUTY_QR_CONTEXT);
		payment.setCollectionContextId(dutyId);
		payment.setExpiresAt(Instant.ofEpochSecond(closeBy));
		payment.setRemarks("Driver duty QR payment initiated");
		payment.setCreatedAt(Instant.now());

		Payment saved = paymentRepo.save(payment);

		return RazorpayQrPayload.builder()
				.paymentId(saved.getId())
				.qrCodeId(qrCodeId)
				.qrImageUrl(qrImageUrl)
				.qrImageContent(qrImageContent)
				.amount(amount)
				.expiresAt(Instant.ofEpochSecond(closeBy))
				.build();
	}

	public QrPaymentStatusResponse isPaidByQR(
			String orgId,
			String bookingId,
			String dutyId
	) throws Exception {

		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		razorpayClientFactory.assertQrEnabled(credentials);

		Payment payment = paymentRepo
				.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						orgId,
						bookingId,
						DRIVER_DUTY_QR_CONTEXT,
						dutyId
				)
				.orElse(null);

		if (payment == null) {
			return QrPaymentStatusResponse.builder()
					.status("NOT_CREATED")
					.paid(false)
					.amount(BigDecimal.ZERO)
					.message("No QR payment was generated")
					.build();
		}

		if (payment.getStatus() == PaymentStatus.CONFIRMED) {
			return buildQrStatusResponse(
					"PAID",
					true,
					payment,
					"Payment received"
			);
		}

		if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(Instant.now())) {
			return buildQrStatusResponse(
					"EXPIRED",
					false,
					payment,
					"QR code expired"
			);
		}

		if (payment.getGatewayQrCodeId() == null || payment.getGatewayQrCodeId().isBlank()) {
			return buildQrStatusResponse(
					"FAILED",
					false,
					payment,
					"QR code id is missing"
			);
		}

		JSONObject response = fetchPaymentsForQr(credentials, payment.getGatewayQrCodeId());

		JSONArray items = response.optJSONArray("items");

		if (items == null || items.isEmpty()) {
			return buildQrStatusResponse(
					"PENDING",
					false,
					payment,
					"Waiting for payment"
			);
		}

		long expectedAmountInPaise = payment.getReceivedAmount()
				.getAmount()
				.setScale(2, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.longValueExact();

		for (int i = 0; i < items.length(); i++) {
			JSONObject item = items.getJSONObject(i);

			String razorpayPaymentId = item.optString("id", null);
			String status = item.optString("status", "");
			long paidAmount = item.optLong("amount", 0);
			String currency = item.optString("currency", "");

			boolean captured = "captured".equalsIgnoreCase(status) || item.optBoolean("captured", false);
			boolean amountMatches = paidAmount == expectedAmountInPaise;
			boolean currencyMatches = credentials.resolvedCurrency().equalsIgnoreCase(currency);

			if (!captured || !amountMatches || !currencyMatches) {
				continue;
			}

			if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
				continue;
			}

			confirmQrPayment(payment, item);

			return buildQrStatusResponse(
					"PAID",
					true,
					payment,
					"Payment received"
			);
		}

		return buildQrStatusResponse(
				"PENDING",
				false,
				payment,
				"Waiting for payment"
		);
	}

	private JSONObject fetchPaymentsForQr(
			RazorpayCredentials credentials,
			String qrCodeId
	) throws Exception {

		String url = credentials.resolvedApiBaseUrl()
				+ "/payments/qr_codes/"
				+ qrCodeId
				+ "/payments?count=10";

		String basicAuth = Base64.getEncoder().encodeToString(
				(credentials.keyId() + ":" + credentials.keySecret())
						.getBytes(StandardCharsets.UTF_8)
		);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Authorization", "Basic " + basicAuth)
				.header("Content-Type", "application/json")
				.timeout(RAZORPAY_HTTP_TIMEOUT)
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(
				request,
				HttpResponse.BodyHandlers.ofString()
		);

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException(
					"Unable to fetch Razorpay QR payments. Status: "
							+ response.statusCode()
							+ ", Body: "
							+ response.body()
			);
		}

		return new JSONObject(response.body());
	}

	private void confirmQrPayment(Payment payment, JSONObject razorpayPayment) {

		if (payment.getStatus() == PaymentStatus.CONFIRMED) {
			return;
		}

		String razorpayPaymentId = razorpayPayment.optString("id", null);

		if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
			throw new IllegalStateException("Razorpay payment id missing");
		}

		if (paymentRepo.existsByGatewayPaymentId(razorpayPaymentId)) {
			return;
		}

		long createdAt = razorpayPayment.optLong("created_at", Instant.now().getEpochSecond());

		payment.setGatewayPaymentId(razorpayPaymentId);
		payment.setTransactionNumber(razorpayPaymentId);
		payment.setTransactionDate(Instant.ofEpochSecond(createdAt));
		payment.setPaymentMode(PaymentMode.UPI);
		payment.setStatus(PaymentStatus.CONFIRMED);
		payment.setRemarks("Driver duty QR payment confirmed through Razorpay QR fetch API");

		Payment saved = paymentRepo.save(payment);

		Booking booking = saved.getBooking();

		PaymentConfirmedEvent event = paymentEventAssembler.toPaymentConfirmedEvent(booking, saved);
		eventPublisher.publishEvent(event);
	}

	private String getRequiredString(QrCode qrCode, String key) {
		Object value = qrCode.get(key);

		if (value == null || value.toString().isBlank()) {
			throw new IllegalStateException("Razorpay QR response missing field: " + key);
		}

		return value.toString();
	}

	private String getOptionalString(QrCode qrCode, String key) {
		Object value = qrCode.get(key);
		return value == null || value.toString().isBlank() ? null : value.toString();
	}

	private QrPaymentStatusResponse buildQrStatusResponse(
			String status,
			boolean paid,
			Payment payment,
			String message
	) {
		return QrPaymentStatusResponse.builder()
				.status(status)
				.paid(paid)
				.amount(payment.getReceivedAmount() != null
						? payment.getReceivedAmount().getAmount()
						: BigDecimal.ZERO)
				.paymentId(payment.getId())
				.razorpayPaymentId(payment.getGatewayPaymentId())
				.qrCodeId(payment.getGatewayQrCodeId())
				.qrImageUrl(payment.getGatewayQrImageUrl())
				.qrImageContent(payment.getGatewayQrImageContent())
				.expiresAt(payment.getExpiresAt())
				.paidAt(payment.getTransactionDate())
				.message(message)
				.build();
	}
}
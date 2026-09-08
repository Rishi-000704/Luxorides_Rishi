package com.core.gateway.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.core.events.PaymentConfirmedEvent;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.gateway.razerpay.QrPaymentStatusResponse;
import com.core.gateway.razerpay.RazorpayCheckoutPayload;
import com.core.gateway.razerpay.RazorpayQrPayload;
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

import lombok.RequiredArgsConstructor;

/*
 * Dev-only stand-in for RazorpayPaymentService -- same shape/methods, but never talks
 * to Razorpay and never moves real money. Only reached when an org has an active
 * PaymentGatewayConfig(gateway=MOCK) row (see DevDataSeeder), so real orgs with real
 * Razorpay config are completely unaffected by this class existing.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MockPaymentService {

	private static final String DRIVER_DUTY_QR_CONTEXT = "DRIVER_DUTY_QR";
	private static final String REMARKS = "Dummy payment (MOCK gateway) -- no real money moved";

	private final BookingRepository bookingRepo;
	private final PaymentRepository paymentRepo;
	private final ClientBookingService clientBookingService;
	private final ApplicationEventPublisher eventPublisher;
	private final PaymentEventAssembler paymentEventAssembler;

	/* ================= CREATE ORDER + CHECKOUT PAYLOAD ================= */

	public RazorpayCheckoutPayload createMockOrder(String bookingId, String clientId, String orgId) {

		Booking booking = bookingRepo.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new IllegalStateException("Booking not found"));

		// P0 IDOR fix -- same ownership guard as RazorpayPaymentService.createRazorpayOrder.
		if (!clientId.equals(booking.getClientId())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "This booking does not belong to you");
		}

		Money pending = calculatePendingAmount(booking);

		if (pending == null || pending.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalStateException("No pending amount to pay");
		}

		long amountInPaise = pending.getAmount()
				.setScale(2, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.longValueExact();

		String mockOrderId = "mock_order_" + UUID.randomUUID();

		Payment payment = new Payment();
		payment.setOrgId(orgId);
		payment.setPaymentMode(PaymentMode.UNKNOWN);
		payment.setBooking(booking);
		payment.setGateway(PaymentGateway.MOCK);
		payment.setGatewayOrderId(mockOrderId);
		payment.setReceivedAmount(pending);
		payment.setTds(Money.INR(BigDecimal.ZERO));
		payment.setStatus(PaymentStatus.INITIATED);
		payment.setCreatedAt(Instant.now());
		payment.setRemarks(REMARKS);

		paymentRepo.save(payment);

		String clientName = booking.getClient() != null && booking.getClient().getName() != null
				? booking.getClient().getName().getDisplayName()
				: null;

		String clientPhone = booking.getClient() != null ? booking.getClient().getPhone() : null;
		String clientEmail = booking.getClient() != null ? booking.getClient().getEmail() : null;

		return RazorpayCheckoutPayload.builder()
				.gateway("MOCK")
				.key(null)
				.orderId(mockOrderId)
				.amount(amountInPaise)
				.currency("INR")
				.prefill(RazorpayCheckoutPayload.Prefill.builder()
						.name(clientName)
						.contact(clientPhone)
						.email(clientEmail)
						.build())
				.build();
	}

	/* ================= CONFIRM ORDER (stand-in for verifyPayment) ================= */

	@SuppressWarnings("null")
	public void confirmMockOrder(String orgId, String bookingId, String orderId) {

		Payment payment = paymentRepo.findByGatewayOrderId(orderId)
				.orElseThrow(() -> new IllegalStateException("Payment not found"));

		if (!orgId.equals(payment.getOrgId())) {
			throw new SecurityException("Payment organization mismatch");
		}

		Booking booking = payment.getBooking();

		if (booking == null || !booking.getBookingId().equals(bookingId)) {
			throw new IllegalStateException("Booking mismatch");
		}

		payment.setGatewayPaymentId("mock_pay_" + UUID.randomUUID());
		payment.setTransactionNumber(payment.getGatewayPaymentId());
		payment.setTransactionDate(Instant.now());
		payment.setPaymentMode(PaymentMode.UPI);
		payment.setStatus(PaymentStatus.CONFIRMED);
		payment.setRemarks(REMARKS);

		Payment saved = paymentRepo.save(payment);

		PaymentConfirmedEvent event = paymentEventAssembler.toPaymentConfirmedEvent(booking, saved);
		eventPublisher.publishEvent(event);

		if (booking.getStatus() == BookingStatus.DRAFT) {
			clientBookingService.confirmBooking(orgId, bookingId);
		}
	}

	/* ================= AMOUNT ================= */

	@SuppressWarnings("null")
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

	/* ================= "QR" FOR DRIVER DUTY END -- AUTO-CONFIRMED, NO REAL SCAN ================= */

	/*
	 * Mirrors RazorpayPaymentService.generateQR's REQUIRES_NEW usage: called from inside
	 * ExternalDriverDutyService.submitEnd's own @Transactional method, which treats
	 * payment-collection failure as non-fatal. Isolating this in its own transaction
	 * keeps that guarantee even though this path can't actually fail the way a real
	 * gateway call can.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@SuppressWarnings("null")
	public RazorpayQrPayload generateMockQr(String orgId, String bookingId, String dutyId, BigDecimal amount) {

		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("QR amount must be greater than zero");
		}

		// Mirrors RazorpayPaymentService.generateQR's existing-payment check --
		// a retried call for a duty that already has a payment must return that
		// same record rather than mint a second CONFIRMED payment for it.
		Payment existingPayment = paymentRepo
				.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						orgId, bookingId, DRIVER_DUTY_QR_CONTEXT, dutyId)
				.orElse(null);

		if (existingPayment != null) {
			return RazorpayQrPayload.builder()
					.paymentId(existingPayment.getId())
					.qrCodeId(existingPayment.getGatewayQrCodeId())
					.qrImageUrl(null)
					.qrImageContent(null)
					.amount(existingPayment.getReceivedAmount().getAmount())
					.expiresAt(existingPayment.getExpiresAt())
					.build();
		}

		Booking booking = bookingRepo.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new IllegalStateException("Booking not found"));

		Instant expiresAt = Instant.now().plus(115, ChronoUnit.MINUTES);
		String qrCodeId = "mock_qr_" + UUID.randomUUID();

		// No real customer scans a QR in local dev -- auto-confirm immediately rather
		// than simulating a pending-then-paid transition nobody is actually waiting on.
		Payment payment = new Payment();
		payment.setOrgId(orgId);
		payment.setBooking(booking);
		payment.setPaymentMode(PaymentMode.UPI);
		payment.setGateway(PaymentGateway.MOCK);
		payment.setReceivedAmount(Money.INR(amount));
		payment.setTds(Money.INR(BigDecimal.ZERO));
		payment.setStatus(PaymentStatus.CONFIRMED);
		payment.setGatewayQrCodeId(qrCodeId);
		payment.setGatewayPaymentId("mock_pay_" + UUID.randomUUID());
		payment.setTransactionNumber(payment.getGatewayPaymentId());
		payment.setTransactionDate(Instant.now());
		payment.setCollectionContext(DRIVER_DUTY_QR_CONTEXT);
		payment.setCollectionContextId(dutyId);
		payment.setExpiresAt(expiresAt);
		payment.setRemarks(REMARKS);
		payment.setCreatedAt(Instant.now());

		Payment saved = paymentRepo.save(payment);

		PaymentConfirmedEvent event = paymentEventAssembler.toPaymentConfirmedEvent(booking, saved);
		eventPublisher.publishEvent(event);

		return RazorpayQrPayload.builder()
				.paymentId(saved.getId())
				.qrCodeId(qrCodeId)
				.qrImageUrl(null)
				.qrImageContent(null)
				.amount(amount)
				.expiresAt(expiresAt)
				.build();
	}

	public QrPaymentStatusResponse mockQrStatus(String orgId, String bookingId, String dutyId) {

		Payment payment = paymentRepo
				.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						orgId, bookingId, DRIVER_DUTY_QR_CONTEXT, dutyId)
				.orElse(null);

		if (payment == null) {
			return QrPaymentStatusResponse.builder()
					.status("NOT_CREATED")
					.paid(false)
					.amount(BigDecimal.ZERO)
					.message("No QR payment was generated")
					.build();
		}

		boolean paid = payment.getStatus() == PaymentStatus.CONFIRMED;

		return QrPaymentStatusResponse.builder()
				.status(paid ? "PAID" : "PENDING")
				.paid(paid)
				.amount(payment.getReceivedAmount() != null ? payment.getReceivedAmount().getAmount() : BigDecimal.ZERO)
				.paymentId(payment.getId())
				.razorpayPaymentId(payment.getGatewayPaymentId())
				.qrCodeId(payment.getGatewayQrCodeId())
				.expiresAt(payment.getExpiresAt())
				.paidAt(payment.getTransactionDate())
				.message(REMARKS)
				.build();
	}
}

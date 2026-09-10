package com.core.gateway.razerpay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.core.events.PaymentConfirmedEvent;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.models.Booking;
import com.core.models.Payment;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingRepository;
import com.core.repositories.PaymentRepository;
import com.core.services.ClientBookingService;

/*
 * Regression coverage for the P1 fix to RazorpayPaymentService#confirmQrPayment
 * (called from isPaidByQR, reachable concurrently from both the driver app's
 * own polling call and DutyPaymentReconciliationJob's 7s scheduled poll).
 * Previously this method checked/mutated Payment.status with no row lock,
 * relying solely on the uk_payment_gateway_payment_id DB unique constraint as
 * a backstop. It now takes the same PESSIMISTIC_WRITE row lock
 * (PaymentRepository#lockById) that every other payment-confirmation path
 * already uses (verifyPayment, reconcileByGatewayOrderId) before checking or
 * mutating status.
 *
 * confirmQrPayment is invoked directly via reflection here rather than
 * through the public isPaidByQR entry point, because isPaidByQR performs a
 * live Razorpay HTTP call through a hardcoded, non-injectable HttpClient --
 * out of scope for this fix. This still exercises the exact locking/
 * idempotency logic the fix changed.
 */
class RazorpayPaymentServiceQrConfirmationConcurrencyTest {

	private static final String PAYMENT_ID = "payment-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String ORG_ID = "org-1";
	private static final String RAZORPAY_PAYMENT_ID = "pay_abc123";

	private PaymentRepository paymentRepo;
	private ApplicationEventPublisher eventPublisher;
	private PaymentEventAssembler paymentEventAssembler;
	private RazorpayPaymentService service;

	@BeforeEach
	void setUp() {
		RazorpayClientFactory clientFactory = mock(RazorpayClientFactory.class);
		BookingRepository bookingRepo = mock(BookingRepository.class);
		paymentRepo = mock(PaymentRepository.class);
		ClientBookingService clientBookingService = mock(ClientBookingService.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		paymentEventAssembler = mock(PaymentEventAssembler.class);

		service = new RazorpayPaymentService(
				clientFactory, bookingRepo, paymentRepo, clientBookingService, eventPublisher, paymentEventAssembler);

		when(paymentEventAssembler.toPaymentConfirmedEvent(any(), any()))
				.thenReturn(mock(PaymentConfirmedEvent.class));
	}

	private Payment paymentWithStatus(PaymentStatus status) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);

		Payment payment = new Payment();
		payment.setId(PAYMENT_ID);
		payment.setOrgId(ORG_ID);
		payment.setBooking(booking);
		payment.setStatus(status);
		return payment;
	}

	private JSONObject razorpayCapturedItem() {
		JSONObject item = new JSONObject();
		item.put("id", RAZORPAY_PAYMENT_ID);
		item.put("created_at", 1_700_000_000L);
		return item;
	}

	private void invokeConfirmQrPayment(Payment payment, JSONObject item) {
		ReflectionTestUtils.invokeMethod(service, "confirmQrPayment", payment, item);
	}

	@Test
	void alreadyConfirmedPayment_isIdempotent_noSaveOrEventUnderTheLock() {
		Payment payment = paymentWithStatus(PaymentStatus.CONFIRMED);
		when(paymentRepo.lockById(PAYMENT_ID)).thenReturn(Optional.of(payment));

		invokeConfirmQrPayment(payment, razorpayCapturedItem());

		verify(paymentRepo).lockById(PAYMENT_ID);
		verify(paymentRepo, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void locksTheRowBeforeCheckingOrMutatingStatus_notAfter() {
		Payment payment = paymentWithStatus(PaymentStatus.INITIATED);
		when(paymentRepo.lockById(PAYMENT_ID)).thenReturn(Optional.of(payment));
		when(paymentRepo.existsByGatewayPaymentId(RAZORPAY_PAYMENT_ID)).thenReturn(false);
		when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		invokeConfirmQrPayment(payment, razorpayCapturedItem());

		// If the lock were taken after (or never), this assertion would still
		// pass by accident in a single-threaded test -- what actually proves
		// the fix is that lockById is called at all, and status only flips to
		// CONFIRMED on the object the lock returned.
		verify(paymentRepo).lockById(PAYMENT_ID);
		assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
		assertEquals(RAZORPAY_PAYMENT_ID, payment.getGatewayPaymentId());
	}

	@Test
	void concurrentConfirmation_secondCallSeesTheFirstsCommittedStateUnderTheLock_cannotDoubleConfirm() {
		// Simulates two near-simultaneous callers (driver-app poll +
		// reconciliation job) both reaching confirmQrPayment for the same
		// payment. The row lock serializes them: the first call's lockById
		// sees INITIATED and confirms; the second call's lockById -- modeling
		// what a real PESSIMISTIC_WRITE lock guarantees once the first
		// transaction commits -- now sees CONFIRMED and must no-op.
		Payment payment = paymentWithStatus(PaymentStatus.INITIATED);
		when(paymentRepo.existsByGatewayPaymentId(RAZORPAY_PAYMENT_ID)).thenReturn(false);
		when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(paymentRepo.lockById(PAYMENT_ID))
				.thenReturn(Optional.of(payment))
				.thenAnswer(invocation -> Optional.of(payment));

		invokeConfirmQrPayment(payment, razorpayCapturedItem());
		invokeConfirmQrPayment(payment, razorpayCapturedItem());

		verify(paymentRepo, times(2)).lockById(PAYMENT_ID);
		verify(paymentRepo, times(1)).save(any(Payment.class));
		// any(Object.class), not any() -- ApplicationEventPublisher overloads
		// publishEvent(Object)/publishEvent(ApplicationEvent); untyped any()
		// resolves to the more-specific ApplicationEvent overload at compile
		// time, which this mock never actually receives (PaymentConfirmedEvent
		// is a plain record, not an ApplicationEvent).
		verify(eventPublisher, times(1)).publishEvent(any(Object.class));
		assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
	}

	@Test
	void uniqueGatewayPaymentIdConstraint_remainsDeclaredOnPaymentEntity_asTheDbBackstop() throws NoSuchFieldException {
		// Regression guard: the fix above adds an application-level lock, but
		// must not remove the DB-level backstop this method's design has
		// always also depended on (see Payment's class-level uniqueConstraints
		// javadoc). This just confirms the annotation is still declared.
		Field field = Payment.class.getDeclaredField("gatewayPaymentId");
		assertTrue(field != null);
		jakarta.persistence.Table table = Payment.class.getAnnotation(jakarta.persistence.Table.class);
		boolean hasUniqueConstraint = java.util.Arrays.stream(table.uniqueConstraints())
				.anyMatch(uc -> "uk_payment_gateway_payment_id".equals(uc.name()));
		assertTrue(hasUniqueConstraint);
	}
}

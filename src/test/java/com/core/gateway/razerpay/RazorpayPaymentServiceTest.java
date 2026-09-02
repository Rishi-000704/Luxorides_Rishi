package com.core.gateway.razerpay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.core.events.PaymentConfirmedEvent;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.models.Booking;
import com.core.models.Client;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.embedded.Name;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingRepository;
import com.core.repositories.PaymentRepository;
import com.core.services.ClientBookingService;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

/*
 * Covers Phase -1.1 Checkpoint A: idempotent checkout-order creation and
 * idempotent payment verification, mirroring the pattern already proven by
 * EstimatePaymentSettlementService and the driver-duty QR flow (neither of
 * which this change touches -- confirmed unchanged by inspection, not
 * re-tested here). RazorpayClient/OrderClient/Order are real SDK classes;
 * Order has a public JSONObject constructor and OrderClient/RazorpayClient
 * are ordinary (non-final) classes with public fields, so real instances are
 * used with Mockito's constructor-bypassing mock() rather than needing any
 * SDK-level test double.
 */
class RazorpayPaymentServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String BOOKING_ID = "booking-1";

	private RazorpayClientFactory clientFactory;
	private BookingRepository bookingRepo;
	private PaymentRepository paymentRepo;
	private ClientBookingService clientBookingService;
	private ApplicationEventPublisher eventPublisher;
	private PaymentEventAssembler paymentEventAssembler;
	private RazorpayPaymentService service;

	private RazorpayCredentials credentials;
	private RazorpayClient razorpayClient;
	private OrderClient orderClient;

	@BeforeEach
	void setUp() throws RazorpayException {
		clientFactory = mock(RazorpayClientFactory.class);
		bookingRepo = mock(BookingRepository.class);
		paymentRepo = mock(PaymentRepository.class);
		clientBookingService = mock(ClientBookingService.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		paymentEventAssembler = mock(PaymentEventAssembler.class);

		service = new RazorpayPaymentService(
				clientFactory, bookingRepo, paymentRepo, clientBookingService, eventPublisher, paymentEventAssembler);

		credentials = new RazorpayCredentials(
				ORG_ID, "key_test", "secret_test", null, "INR", "Fleetovo", "Fleetovo", true, true, false);
		when(clientFactory.credentials(ORG_ID)).thenReturn(credentials);

		razorpayClient = mock(RazorpayClient.class);
		orderClient = mock(OrderClient.class);
		razorpayClient.orders = orderClient;
		when(clientFactory.client(credentials)).thenReturn(razorpayClient);
	}

	private Booking bookingWithPending(BigDecimal totalAmount, BigDecimal alreadyPaid) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setStatus(BookingStatus.DRAFT);
		booking.setTotal(Money.INR(totalAmount));

		Client client = new Client();
		Name name = new Name();
		name.setFirstName("Test");
		name.setLastName("Client");
		client.setName(name);
		client.setPhone("9999999999");
		client.setEmail("test@example.com");
		booking.setClient(client);

		if (alreadyPaid != null && alreadyPaid.compareTo(BigDecimal.ZERO) > 0) {
			Payment confirmed = new Payment();
			confirmed.setStatus(PaymentStatus.CONFIRMED);
			confirmed.setReceivedAmount(Money.INR(alreadyPaid));
			booking.setPayments(java.util.List.of(confirmed));
		}

		return booking;
	}

	private Payment outstandingPayment(BigDecimal amount, Instant expiresAt, String gatewayOrderId) {
		Payment payment = new Payment();
		payment.setId("payment-existing");
		payment.setOrgId(ORG_ID);
		payment.setGateway(PaymentGateway.RAZORPAY);
		payment.setStatus(PaymentStatus.INITIATED);
		payment.setReceivedAmount(Money.INR(amount));
		payment.setExpiresAt(expiresAt);
		payment.setGatewayOrderId(gatewayOrderId);
		return payment;
	}

	/* ================= A1: ORDER CREATION ================= */

	@Test
	void createOrder_createsNewPayment_whenNoneOutstanding() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED))
				.thenReturn(Optional.empty());

		Order createdOrder = new Order(new JSONObject().put("id", "order_new123"));
		when(orderClient.create(any(JSONObject.class))).thenReturn(createdOrder);

		RazorpayCheckoutPayload payload = service.createRazorpayOrder(BOOKING_ID, ORG_ID);

		assertEquals("order_new123", payload.getOrderId());
		assertEquals(100000L, payload.getAmount());
		verify(orderClient, times(1)).create(any(JSONObject.class));
		verify(paymentRepo, times(1)).save(any(Payment.class));
	}

	@Test
	void createOrder_reusesExistingOrder_whenValidOutstandingPaymentExists() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		Payment existing = outstandingPayment(
				new BigDecimal("1000.00"), Instant.now().plus(10, ChronoUnit.MINUTES), "order_existing456");
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(existing));

		RazorpayCheckoutPayload payload = service.createRazorpayOrder(BOOKING_ID, ORG_ID);

		assertEquals("order_existing456", payload.getOrderId());
		verify(orderClient, never()).create(any(JSONObject.class));
		verify(paymentRepo, never()).save(any(Payment.class));
	}

	@Test
	void createOrder_doesNotReuse_whenAmountMismatched() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		Payment stale = outstandingPayment(
				new BigDecimal("500.00"), Instant.now().plus(10, ChronoUnit.MINUTES), "order_stale789");
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(stale));

		Order createdOrder = new Order(new JSONObject().put("id", "order_fresh999"));
		when(orderClient.create(any(JSONObject.class))).thenReturn(createdOrder);

		RazorpayCheckoutPayload payload = service.createRazorpayOrder(BOOKING_ID, ORG_ID);

		assertEquals("order_fresh999", payload.getOrderId());
		verify(orderClient, times(1)).create(any(JSONObject.class));
	}

	@Test
	void createOrder_doesNotReuse_whenExistingPaymentExpired() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		Payment expired = outstandingPayment(
				new BigDecimal("1000.00"), Instant.now().minus(1, ChronoUnit.MINUTES), "order_expired000");
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(expired));

		Order createdOrder = new Order(new JSONObject().put("id", "order_fresh111"));
		when(orderClient.create(any(JSONObject.class))).thenReturn(createdOrder);

		RazorpayCheckoutPayload payload = service.createRazorpayOrder(BOOKING_ID, ORG_ID);

		assertEquals("order_fresh111", payload.getOrderId());
		verify(orderClient, times(1)).create(any(JSONObject.class));
	}

	@Test
	void createOrder_looksUpReuseCandidateScopedToSameOrgAndBooking() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED))
				.thenReturn(Optional.empty());
		when(orderClient.create(any(JSONObject.class)))
				.thenReturn(new Order(new JSONObject().put("id", "order_x")));

		service.createRazorpayOrder(BOOKING_ID, ORG_ID);

		// Reuse lookup is scoped by orgId + bookingId together -- structurally
		// impossible to match another org's or another booking's payment.
		verify(paymentRepo, times(1)).findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				eq(ORG_ID), eq(BOOKING_ID), eq(PaymentGateway.RAZORPAY), eq(PaymentStatus.INITIATED));
	}

	@Test
	void createOrder_locksBookingRow_notPlainFind() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				any(), any(), any(), any())).thenReturn(Optional.empty());
		when(orderClient.create(any(JSONObject.class)))
				.thenReturn(new Order(new JSONObject().put("id", "order_y")));

		service.createRazorpayOrder(BOOKING_ID, ORG_ID);

		verify(bookingRepo, times(1)).lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID);
		verify(bookingRepo, never()).findByBookingIdAndOrgId(any(), any());
	}

	/* ================= A3: PAYMENT VERIFY ================= */

	private Payment initiatedCheckoutPayment(String orderId) {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		Payment payment = new Payment();
		payment.setId("payment-1");
		payment.setOrgId(ORG_ID);
		payment.setBooking(booking);
		payment.setGateway(PaymentGateway.RAZORPAY);
		payment.setGatewayOrderId(orderId);
		payment.setReceivedAmount(Money.INR(new BigDecimal("1000.00")));
		payment.setStatus(PaymentStatus.INITIATED);
		return payment;
	}

	@Test
	void verifyPayment_confirmsPayment_onFirstValidVerification() throws Exception {
		Payment payment = initiatedCheckoutPayment("order_abc");
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(paymentRepo.existsByGatewayPaymentIdAndIdNot("pay_xyz", "payment-1")).thenReturn(false);
		when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

		com.razorpay.Payment gatewayPayment = new com.razorpay.Payment(new JSONObject().put("method", "upi"));
		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		when(paymentClient.fetch("pay_xyz")).thenReturn(gatewayPayment);

		when(paymentEventAssembler.toPaymentConfirmedEvent(any(Booking.class), any(Payment.class)))
				.thenReturn(mock(PaymentConfirmedEvent.class));

		service.verifyPayment(ORG_ID, BOOKING_ID, "order_abc", "pay_xyz", "sig");

		assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
		verify(eventPublisher, times(1)).publishEvent(any(PaymentConfirmedEvent.class));
		verify(clientBookingService, times(1)).confirmBooking(ORG_ID, BOOKING_ID);
	}

	@Test
	void verifyPayment_isIdempotent_onRepeatedVerification() throws Exception {
		Payment payment = initiatedCheckoutPayment("order_abc");
		payment.setStatus(PaymentStatus.CONFIRMED);
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		service.verifyPayment(ORG_ID, BOOKING_ID, "order_abc", "pay_xyz", "sig");

		verify(eventPublisher, never()).publishEvent(any());
		verify(clientBookingService, never()).confirmBooking(any(), any());
		verify(paymentRepo, never()).save(any(Payment.class));
	}

	@Test
	void verifyPayment_rejectsCrossPaymentIdReuse() throws Exception {
		Payment payment = initiatedCheckoutPayment("order_abc");
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(paymentRepo.existsByGatewayPaymentIdAndIdNot("pay_xyz", "payment-1")).thenReturn(true);

		assertThrows(IllegalStateException.class,
				() -> service.verifyPayment(ORG_ID, BOOKING_ID, "order_abc", "pay_xyz", "sig"));

		assertEquals(PaymentStatus.INITIATED, payment.getStatus());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void verifyPayment_rejectsOrgMismatch() {
		Payment payment = initiatedCheckoutPayment("order_abc");
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		assertThrows(SecurityException.class,
				() -> service.verifyPayment("different-org", BOOKING_ID, "order_abc", "pay_xyz", "sig"));

		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void verifyPayment_rejectsBookingMismatch() {
		Payment payment = initiatedCheckoutPayment("order_abc");
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		assertThrows(IllegalStateException.class,
				() -> service.verifyPayment(ORG_ID, "different-booking", "order_abc", "pay_xyz", "sig"));

		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void verifyPayment_locksPaymentRow_notPlainFind() throws Exception {
		Payment payment = initiatedCheckoutPayment("order_abc");
		payment.setStatus(PaymentStatus.CONFIRMED);
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		service.verifyPayment(ORG_ID, BOOKING_ID, "order_abc", "pay_xyz", "sig");

		verify(paymentRepo, times(1)).lockByGatewayOrderId("order_abc");
		verify(paymentRepo, never()).findByGatewayOrderId(any());
	}

	/* ================= P1.7: REFUND ================= */

	private Booking bookingWithConfirmedPayment(String gatewayPaymentId, BigDecimal amount) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setTotal(Money.INR(amount));

		Payment confirmed = new Payment();
		confirmed.setId("payment-confirmed");
		confirmed.setStatus(PaymentStatus.CONFIRMED);
		confirmed.setGatewayPaymentId(gatewayPaymentId);
		confirmed.setReceivedAmount(Money.INR(amount));
		booking.setPayments(java.util.List.of(confirmed));

		return booking;
	}

	@Test
	void refundPayment_refundsTheConfirmedGatewayPayment_andMarksItRefunded() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		com.razorpay.Refund refund = new com.razorpay.Refund(new JSONObject().put("id", "rfnd_123"));
		when(paymentClient.refund(eq("pay_captured"), any(JSONObject.class))).thenReturn(refund);

		String refundId = service.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("400.00"));

		assertEquals("rfnd_123", refundId);
		assertEquals(PaymentStatus.REFUNDED, booking.getPayments().get(0).getStatus());
		verify(paymentRepo, times(1)).save(any(Payment.class));
	}

	@Test
	void refundPayment_noConfirmedPayment_throwsWithoutCallingRazorpay() {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setPayments(java.util.List.of());
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		assertThrows(IllegalStateException.class,
				() -> service.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("100.00")));

		verify(paymentRepo, never()).save(any(Payment.class));
	}

	/*
	 * A second refundPayment call for the same booking (e.g. from a second,
	 * duplicate RefundRequest -- see RefundRequestServiceTest) must not
	 * refund again: once the first call's REFUNDED status is visible, no
	 * CONFIRMED payment remains for this booking to match.
	 */
	@Test
	void refundPayment_secondCallAfterAlreadyRefunded_findsNoConfirmedPaymentToRefundAgain() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		booking.getPayments().get(0).setStatus(PaymentStatus.REFUNDED);
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		assertThrows(IllegalStateException.class,
				() -> service.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("400.00")));

		verify(paymentRepo, never()).save(any(Payment.class));
	}
}

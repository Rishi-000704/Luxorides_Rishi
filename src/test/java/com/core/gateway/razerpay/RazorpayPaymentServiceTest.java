package com.core.gateway.razerpay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
				ORG_ID, "key_test", "secret_test", null, "INR", "Fleetovo", "Fleetovo", true, true, false, "whsec_test");
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
		when(orderClient.fetch("order_existing456"))
				.thenReturn(new Order(new JSONObject().put("status", "created")));

		RazorpayCheckoutPayload payload = service.createRazorpayOrder(BOOKING_ID, ORG_ID);

		assertEquals("order_existing456", payload.getOrderId());
		verify(orderClient, never()).create(any(JSONObject.class));
		verify(paymentRepo, never()).save(any(Payment.class));
	}

	/* ================= P1D: LIVE REUSE-SAFETY CHECK ================= */

	@Test
	void createOrder_reuses_whenExistingOrderStillAttempted() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		Payment existing = outstandingPayment(
				new BigDecimal("1000.00"), Instant.now().plus(10, ChronoUnit.MINUTES), "order_existing456");
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(existing));
		when(orderClient.fetch("order_existing456"))
				.thenReturn(new Order(new JSONObject().put("status", "attempted")));

		RazorpayCheckoutPayload payload = service.createRazorpayOrder(BOOKING_ID, ORG_ID);

		assertEquals("order_existing456", payload.getOrderId());
		verify(orderClient, never()).create(any(JSONObject.class));
	}

	@Test
	void createOrder_refusesToReuse_whenRazorpayAlreadyReportsOrderPaid() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		Payment existing = outstandingPayment(
				new BigDecimal("1000.00"), Instant.now().plus(10, ChronoUnit.MINUTES), "order_already_paid");
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(existing));
		when(orderClient.fetch("order_already_paid"))
				.thenReturn(new Order(new JSONObject().put("status", "paid")));

		assertThrows(IllegalStateException.class, () -> service.createRazorpayOrder(BOOKING_ID, ORG_ID));

		verify(orderClient, never()).create(any(JSONObject.class));
		verify(paymentRepo, never()).save(any(Payment.class));
	}

	@Test
	void createOrder_throws_whenExistingOrderStateCannotBeVerified() throws Exception {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		when(bookingRepo.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		Payment existing = outstandingPayment(
				new BigDecimal("1000.00"), Instant.now().plus(10, ChronoUnit.MINUTES), "order_unreachable");
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndGatewayAndStatusOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, PaymentGateway.RAZORPAY, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(existing));
		when(orderClient.fetch("order_unreachable")).thenThrow(new RazorpayException("network error"));

		assertThrows(IllegalStateException.class, () -> service.createRazorpayOrder(BOOKING_ID, ORG_ID));

		// Never create a second order while Razorpay's state can't be established --
		// that would risk a duplicate charge.
		verify(orderClient, never()).create(any(JSONObject.class));
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

	/* ================= P1H: REFUND PROVIDER-SIDE IDEMPOTENCY ================= */

	@Test
	void refundPayment_reusesExistingRazorpayRefund_insteadOfIssuingADuplicate() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;

		com.razorpay.Refund existingRefund = new com.razorpay.Refund(
				new JSONObject().put("id", "rfnd_existing").put("amount", 40000L));
		when(paymentClient.fetchAllRefunds("pay_captured")).thenReturn(java.util.List.of(existingRefund));

		String refundId = service.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("400.00"));

		assertEquals("rfnd_existing", refundId);
		verify(paymentClient, never()).refund(any(), any(JSONObject.class));
		assertEquals(PaymentStatus.REFUNDED, booking.getPayments().get(0).getStatus());
	}

	@Test
	void refundPayment_persistenceFailureAfterProviderSuccess_throwsRefundPersistenceExceptionWithRefundId() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		com.razorpay.Refund refund = new com.razorpay.Refund(new JSONObject().put("id", "rfnd_999"));
		when(paymentClient.refund(eq("pay_captured"), any(JSONObject.class))).thenReturn(refund);
		when(paymentRepo.save(any(Payment.class))).thenThrow(new RuntimeException("db unavailable"));

		RefundPersistenceException thrown = assertThrows(RefundPersistenceException.class,
				() -> service.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("400.00")));

		assertEquals("rfnd_999", thrown.getRefundId());
	}

	/* ================= P1B/1C/1E: RECOVERY VIA PROVIDER RECONCILIATION ================= */

	private Payment initiatedCheckoutPaymentWithBooking(String orderId, BigDecimal amount) {
		Booking booking = bookingWithPending(new BigDecimal("1000.00"), null);
		Payment payment = new Payment();
		payment.setId("payment-1");
		payment.setOrgId(ORG_ID);
		payment.setBooking(booking);
		payment.setGateway(PaymentGateway.RAZORPAY);
		payment.setGatewayOrderId(orderId);
		payment.setReceivedAmount(Money.INR(amount));
		payment.setStatus(PaymentStatus.INITIATED);
		return payment;
	}

	private Order paidOrder(String orderId, long amountInPaise) {
		return new Order(new JSONObject().put("id", orderId).put("amount", amountInPaise).put("currency", "INR"));
	}

	private com.razorpay.Payment capturedCandidate(String paymentId, String orderId, long amountInPaise, String currency) {
		return new com.razorpay.Payment(new JSONObject()
				.put("id", paymentId)
				.put("order_id", orderId)
				.put("amount", amountInPaise)
				.put("currency", currency)
				.put("status", "captured")
				.put("captured", true)
				.put("method", "upi")
				.put("created_at", 1700000000L));
	}

	@Test
	void reconcileByGatewayOrderId_confirmsPayment_whenProviderShowsCapturedMatch() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(paymentRepo.existsByGatewayPaymentIdAndIdNot("pay_captured", "payment-1")).thenReturn(false);
		when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
		when(paymentEventAssembler.toPaymentConfirmedEvent(any(Booking.class), any(Payment.class)))
				.thenReturn(mock(PaymentConfirmedEvent.class));

		when(orderClient.fetch("order_abc")).thenReturn(paidOrder("order_abc", 100000L));
		when(orderClient.fetchPayments("order_abc"))
				.thenReturn(java.util.List.of(capturedCandidate("pay_captured", "order_abc", 100000L, "INR")));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
		assertEquals("pay_captured", payment.getGatewayPaymentId());
		verify(eventPublisher, times(1)).publishEvent(any(PaymentConfirmedEvent.class));
		verify(clientBookingService, times(1)).confirmBooking(ORG_ID, BOOKING_ID);
	}

	@Test
	void reconcileByGatewayOrderId_isIdempotent_whenAlreadyConfirmed() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		payment.setStatus(PaymentStatus.CONFIRMED);
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		verify(orderClient, never()).fetch(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void reconcileByGatewayOrderId_recoversFromFailedStatus_onLateProviderConfirmation() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		payment.setStatus(PaymentStatus.FAILED);
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(paymentRepo.existsByGatewayPaymentIdAndIdNot("pay_captured", "payment-1")).thenReturn(false);
		when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
		when(paymentEventAssembler.toPaymentConfirmedEvent(any(Booking.class), any(Payment.class)))
				.thenReturn(mock(PaymentConfirmedEvent.class));
		when(orderClient.fetch("order_abc")).thenReturn(paidOrder("order_abc", 100000L));
		when(orderClient.fetchPayments("order_abc"))
				.thenReturn(java.util.List.of(capturedCandidate("pay_captured", "order_abc", 100000L, "INR")));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
	}

	@Test
	void reconcileByGatewayOrderId_doesNotTouch_cancelledOrRefundedPayments() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		payment.setStatus(PaymentStatus.REFUNDED);
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
		verify(orderClient, never()).fetch(any());
	}

	@Test
	void reconcileByGatewayOrderId_rejectsOrgMismatch() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		assertThrows(SecurityException.class, () -> service.reconcileByGatewayOrderId("different-org", "order_abc"));

		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void reconcileByGatewayOrderId_skipsCandidate_onAmountMismatch() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(orderClient.fetch("order_abc")).thenReturn(paidOrder("order_abc", 100000L));
		when(orderClient.fetchPayments("order_abc"))
				.thenReturn(java.util.List.of(capturedCandidate("pay_wrong_amount", "order_abc", 50000L, "INR")));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		assertEquals(PaymentStatus.INITIATED, payment.getStatus());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void reconcileByGatewayOrderId_skipsCandidate_onCurrencyMismatch() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(orderClient.fetch("order_abc")).thenReturn(paidOrder("order_abc", 100000L));
		when(orderClient.fetchPayments("order_abc"))
				.thenReturn(java.util.List.of(capturedCandidate("pay_wrong_currency", "order_abc", 100000L, "USD")));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		assertEquals(PaymentStatus.INITIATED, payment.getStatus());
	}

	@Test
	void reconcileByGatewayOrderId_skipsCandidate_onWrongOrderIdentity() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(orderClient.fetch("order_abc")).thenReturn(paidOrder("order_abc", 100000L));
		when(orderClient.fetchPayments("order_abc"))
				.thenReturn(java.util.List.of(capturedCandidate("pay_wrong_order", "order_different", 100000L, "INR")));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		assertEquals(PaymentStatus.INITIATED, payment.getStatus());
	}

	@Test
	void reconcileByGatewayOrderId_skipsCandidate_whenNotCaptured() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(orderClient.fetch("order_abc")).thenReturn(paidOrder("order_abc", 100000L));

		com.razorpay.Payment notCaptured = new com.razorpay.Payment(new JSONObject()
				.put("id", "pay_pending").put("order_id", "order_abc")
				.put("amount", 100000L).put("currency", "INR")
				.put("status", "authorized").put("captured", false));
		when(orderClient.fetchPayments("order_abc")).thenReturn(java.util.List.of(notCaptured));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		assertEquals(PaymentStatus.INITIATED, payment.getStatus());
	}

	@Test
	void reconcileByGatewayOrderId_refusesToConfirm_whenPaymentIdAlreadyLinkedElsewhere() throws Exception {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(paymentRepo.existsByGatewayPaymentIdAndIdNot("pay_captured", "payment-1")).thenReturn(true);
		when(orderClient.fetch("order_abc")).thenReturn(paidOrder("order_abc", 100000L));
		when(orderClient.fetchPayments("order_abc"))
				.thenReturn(java.util.List.of(capturedCandidate("pay_captured", "order_abc", 100000L, "INR")));

		service.reconcileByGatewayOrderId(ORG_ID, "order_abc");

		assertEquals(PaymentStatus.INITIATED, payment.getStatus());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void reconcileByGatewayOrderId_noOp_whenNoLocalPaymentFoundForOrder() throws Exception {
		when(paymentRepo.lockByGatewayOrderId("order_unknown")).thenReturn(Optional.empty());

		service.reconcileByGatewayOrderId(ORG_ID, "order_unknown");

		verify(orderClient, never()).fetch(any());
	}

	/* ================= P1C: EXPIRY OF UNRECOVERABLE CHECKOUT PAYMENTS ================= */

	@Test
	void expireIfStillInitiatedPastGracePeriod_marksFailed_whenPastGraceAndStillInitiated() {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		payment.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));
		when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

		service.expireIfStillInitiatedPastGracePeriod("order_abc", Instant.now().minus(1, ChronoUnit.HOURS));

		assertEquals(PaymentStatus.FAILED, payment.getStatus());
	}

	@Test
	void expireIfStillInitiatedPastGracePeriod_leavesUnchanged_whenNotYetPastGrace() {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		payment.setExpiresAt(Instant.now().minus(10, ChronoUnit.MINUTES));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		service.expireIfStillInitiatedPastGracePeriod("order_abc", Instant.now().minus(1, ChronoUnit.HOURS));

		assertEquals(PaymentStatus.INITIATED, payment.getStatus());
		verify(paymentRepo, never()).save(any(Payment.class));
	}

	@Test
	void expireIfStillInitiatedPastGracePeriod_leavesUnchanged_whenAlreadyConfirmed() {
		Payment payment = initiatedCheckoutPaymentWithBooking("order_abc", new BigDecimal("1000.00"));
		payment.setStatus(PaymentStatus.CONFIRMED);
		payment.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
		when(paymentRepo.lockByGatewayOrderId("order_abc")).thenReturn(Optional.of(payment));

		service.expireIfStillInitiatedPastGracePeriod("order_abc", Instant.now().minus(1, ChronoUnit.HOURS));

		assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
		verify(paymentRepo, never()).save(any(Payment.class));
	}

	/* ================= REFUND-RECOVERY: verifyRefund (Ops Phase 3/5) ================= */

	@Test
	void verifyRefund_confirmsViaTargetedLookup_whenKnownRefundIdMatches() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		com.razorpay.Refund refund = new com.razorpay.Refund(new JSONObject().put("id", "rfnd_real").put("amount", 40000L));
		when(paymentClient.fetchRefund("pay_captured", "rfnd_real")).thenReturn(refund);

		RazorpayPaymentService.RefundVerification result =
				service.verifyRefund(ORG_ID, BOOKING_ID, "rfnd_real", new BigDecimal("400.00"));

		assertEquals("rfnd_real", result.refundId());
		verify(paymentClient, never()).fetchAllRefunds(anyString());
	}

	@Test
	void verifyRefund_fallsBackToBroaderSearch_whenTargetedLookupFails() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		when(paymentClient.fetchRefund("pay_captured", "rfnd_real")).thenThrow(new RazorpayException("not found"));

		com.razorpay.Refund found = new com.razorpay.Refund(new JSONObject().put("id", "rfnd_real").put("amount", 40000L));
		when(paymentClient.fetchAllRefunds("pay_captured")).thenReturn(java.util.List.of(found));

		RazorpayPaymentService.RefundVerification result =
				service.verifyRefund(ORG_ID, BOOKING_ID, "rfnd_real", new BigDecimal("400.00"));

		assertEquals("rfnd_real", result.refundId());
	}

	@Test
	void verifyRefund_broaderSearch_whenNoKnownRefundId() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		com.razorpay.Refund found = new com.razorpay.Refund(new JSONObject().put("id", "rfnd_found").put("amount", 40000L));
		when(paymentClient.fetchAllRefunds("pay_captured")).thenReturn(java.util.List.of(found));

		RazorpayPaymentService.RefundVerification result =
				service.verifyRefund(ORG_ID, BOOKING_ID, null, new BigDecimal("400.00"));

		assertEquals("rfnd_found", result.refundId());
		verify(paymentClient, never()).fetchRefund(any(), any());
	}

	@Test
	void verifyRefund_returnsNoRefundId_whenNoneExistsAtProvider() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		when(paymentClient.fetchAllRefunds("pay_captured")).thenReturn(java.util.List.of());

		RazorpayPaymentService.RefundVerification result =
				service.verifyRefund(ORG_ID, BOOKING_ID, null, new BigDecimal("400.00"));

		assertEquals(null, result.refundId());
	}

	@Test
	void verifyRefund_marksLocalPaymentRefunded_whenConfirmedByProvider() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		com.razorpay.Refund refund = new com.razorpay.Refund(new JSONObject().put("id", "rfnd_real").put("amount", 40000L));
		when(paymentClient.fetchRefund("pay_captured", "rfnd_real")).thenReturn(refund);

		service.verifyRefund(ORG_ID, BOOKING_ID, "rfnd_real", new BigDecimal("400.00"));

		assertEquals(PaymentStatus.REFUNDED, booking.getPayments().get(0).getStatus());
		verify(paymentRepo, times(1)).save(any(Payment.class));
	}

	@Test
	void verifyRefund_worksAgainstAlreadyRefundedLocalPayment_forLateVerification() throws Exception {
		Booking booking = bookingWithConfirmedPayment("pay_captured", new BigDecimal("1000.00"));
		booking.getPayments().get(0).setStatus(PaymentStatus.REFUNDED);
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		com.razorpay.PaymentClient paymentClient = mock(com.razorpay.PaymentClient.class);
		razorpayClient.payments = paymentClient;
		com.razorpay.Refund refund = new com.razorpay.Refund(new JSONObject().put("id", "rfnd_real").put("amount", 40000L));
		when(paymentClient.fetchRefund("pay_captured", "rfnd_real")).thenReturn(refund);

		RazorpayPaymentService.RefundVerification result =
				service.verifyRefund(ORG_ID, BOOKING_ID, "rfnd_real", new BigDecimal("400.00"));

		assertEquals("rfnd_real", result.refundId());
		// Already REFUNDED -- must not re-save/no-op path taken.
		verify(paymentRepo, never()).save(any(Payment.class));
	}
}

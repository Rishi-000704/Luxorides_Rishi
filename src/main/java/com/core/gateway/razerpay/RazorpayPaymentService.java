package com.core.gateway.razerpay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.core.events.PaymentConfirmedEvent;
import com.core.events.assembler.PaymentEventAssembler;
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

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RazorpayPaymentService {

	private final RazorpayClientFactory razorpayClientFactory;
	private final BookingRepository bookingRepo;
	private final PaymentRepository paymentRepo;
	private final ClientBookingService clientBookingService;
	private final ApplicationEventPublisher eventPublisher;
	private final PaymentEventAssembler paymentEventAssembler;

	private static final String DRIVER_DUTY_QR_CONTEXT = "DRIVER_DUTY_QR";

	private final HttpClient httpClient = HttpClient.newHttpClient();

	/* ================= PUBLIC KEY ================= */

	public String getPublicKey(String orgId) {
		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		return credentials.keyId();
	}

	/* ================= CREATE ORDER + CHECKOUT PAYLOAD ================= */

	public RazorpayCheckoutPayload createRazorpayOrder(String bookingId, String orgId) throws Exception {

		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);
		razorpayClientFactory.assertCheckoutEnabled(credentials);

		RazorpayClient razorpayClient = razorpayClientFactory.client(credentials);

		Booking booking = bookingRepo.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new IllegalStateException("Booking not found"));

		Money pending = calculatePendingAmount(booking);

		if (pending == null || pending.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalStateException("No pending amount to pay");
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

		Payment payment = paymentRepo.findByGatewayOrderId(orderId)
				.orElseThrow(() -> new IllegalStateException("Payment not found"));

		if (!orgId.equals(payment.getOrgId())) {
			throw new SecurityException("Payment organization mismatch");
		}

		Booking booking = payment.getBooking();

		if (booking == null || !booking.getBookingId().equals(bookingId)) {
			throw new IllegalStateException("Booking mismatch");
		}

		razorpayClientFactory.verifySignature(credentials, orderId, paymentId, signature);

		com.razorpay.Payment razorpayPayment = razorpayClient.payments.fetch(paymentId);

		String method = razorpayPayment.get("method");

		payment.setGatewayPaymentId(paymentId);
		payment.setGatewaySignature(signature);
		payment.setTransactionNumber(paymentId);
		payment.setTransactionDate(Instant.now());
		payment.setPaymentMode(PaymentUtil.mapPaymentMode(method));
		payment.setStatus(PaymentStatus.CONFIRMED);

		Payment saved = paymentRepo.save(payment);

		PaymentConfirmedEvent event = paymentEventAssembler.toPaymentConfirmedEvent(booking, saved);
		eventPublisher.publishEvent(event);

		if (booking.getStatus() == BookingStatus.DRAFT) {
			clientBookingService.confirmBooking(orgId, bookingId);
		}
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
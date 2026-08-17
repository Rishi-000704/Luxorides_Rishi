package com.core.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.estimate.EstimatePaymentInitResponse;
import com.core.dtos.estimate.EstimatePaymentStatusResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.gateway.razerpay.RazorpayClientFactory;
import com.core.gateway.razerpay.RazorpayCredentials;
import com.core.models.Booking;
import com.core.models.Client;
import com.core.models.Estimate;
import com.core.models.EstimateAccessToken;
import com.core.models.EstimateEntry;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.enums.EstimateLinkStatus;
import com.core.models.enums.EstimateStatus;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentMode;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.EstimateAccessTokenRepository;
import com.core.repositories.EstimateRepository;
import com.core.repositories.PaymentRepository;
import com.core.util.EstimateUtil;
import com.core.util.PaymentUtil;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicEstimateService {

	private static final List<PaymentStatus>
			ACTIVE_PAYMENT_STATUSES = List.of(
			PaymentStatus.INITIATED,
			PaymentStatus.PENDING);

	private final EstimateAccessTokenRepository tokenRepository;
	private final EstimateRepository estimateRepository;
	private final PaymentRepository paymentRepository;

	private final ClientService clientService;

	private final EstimateConversionService
			estimateConversionService;

	private final EstimatePaymentSettlementService
			settlementService;

	private final RazorpayClientFactory
			razorpayClientFactory;

	@Transactional
	public Estimate viewEstimate(
			String rawToken) {

		ResolvedEstimate resolved =
				resolve(rawToken);

		Estimate estimate =
				resolved.estimate();

		EstimateAccessToken token =
				resolved.token();

		token.setLastViewedAt(
				Instant.now());

		tokenRepository.save(token);

		if (estimate.getStatus()
				== EstimateStatus.SENT
				|| estimate.getStatus()
				== EstimateStatus.DRAFT) {

			estimate.setStatus(
					EstimateStatus.VIEWED);

			estimate.setViewedAt(
					Instant.now());

			estimateRepository.save(estimate);
		}

		/*
		 * The assembler runs after this transactional service
		 * method returns. Initialise public lazy relations here.
		 */
		initializePublicRelations(estimate);

		return estimate;
	}

	@Transactional
	public EstimatePaymentInitResponse createPayment(
			String rawToken) throws Exception {

		ResolvedEstimate resolved =
				resolve(rawToken);

		/*
		 * Lock the estimate so two simultaneous payment button
		 * clicks cannot create two new orders.
		 */
		Estimate estimate = estimateRepository
				.lockByIdAndOrgId(
						resolved.estimate().getId(),
						resolved.estimate().getOrgId())
				.orElseThrow(() ->
						new NotFoundException(
								ErrorCode.ESTIMATE_NOT_FOUND,
								"Estimate not found"));

		attachData(estimate);

		assertEstimateCanAcceptPayment(
				estimate);

		RazorpayCredentials credentials =
				razorpayClientFactory.credentials(
						estimate.getOrgId());

		razorpayClientFactory
				.assertCheckoutEnabled(
						credentials);

		Money payableNow =
				payableNow(estimate);

		validatePayableAmount(
				payableNow);

		validateCurrency(
				payableNow,
				credentials);

		long amountInSubunits =
				toSubunits(
						payableNow.getAmount());

		String currency =
				payableNow.getCurrency().name();

		RazorpayClient razorpayClient =
				razorpayClientFactory.client(
						credentials);

		/*
		 * First locate the newest unresolved payment.
		 */
		Payment pendingPayment =
				paymentRepository
						.findTopByOrgIdAndEstimate_IdAndStatusInOrderByCreatedAtDesc(
								estimate.getOrgId(),
								estimate.getId(),
								ACTIVE_PAYMENT_STATUSES)
						.orElse(null);

		if (pendingPayment != null) {

			EstimatePaymentInitResponse reusable =
					tryReusePendingPayment(
							estimate,
							pendingPayment,
							razorpayClient,
							credentials,
							amountInSubunits,
							currency);

			if (reusable != null) {

				estimate.setStatus(
						EstimateStatus.PAYMENT_INITIATED);

				if (estimate.getPaymentInitiatedAt()
						== null) {

					estimate.setPaymentInitiatedAt(
							Instant.now());
				}

				estimateRepository.save(estimate);

				return reusable;
			}
		}

		/*
		 * No reusable pending order exists.
		 * Create a new Razorpay order.
		 */
		JSONObject options =
				new JSONObject();

		options.put(
				"amount",
				amountInSubunits);

		options.put(
				"currency",
				currency);

		options.put(
				"receipt",
				estimate.getEstimateId());

		options.put(
				"notes",
				new JSONObject()
						.put(
								"context",
								"ESTIMATE_LINK")
						.put(
								"orgId",
								estimate.getOrgId())
						.put(
								"estimateId",
								estimate.getEstimateId())
						.put(
								"estimateDbId",
								estimate.getId()));

		Order order =
				razorpayClient.orders
						.create(options);

		Payment payment =
				new Payment();

		payment.setOrgId(
				estimate.getOrgId());

		payment.setEstimate(estimate);

		payment.setPaymentMode(
				PaymentMode.UNKNOWN);

		payment.setGateway(
				PaymentGateway.RAZORPAY);

		payment.setGatewayOrderId(
				valueAsString(
						order,
						"id"));

		payment.setReceivedAmount(
				copyMoney(payableNow));

		payment.setTds(
				new Money(
						BigDecimal.ZERO.setScale(
								2,
								RoundingMode.HALF_UP),
						payableNow.getCurrency()));

		payment.setStatus(
				PaymentStatus.INITIATED);

		payment.setRemarks(
				"Estimate payment: "
						+ estimate.getEstimateId());

		paymentRepository.save(payment);

		estimate.setStatus(
				EstimateStatus.PAYMENT_INITIATED);

		estimate.setPaymentInitiatedAt(
				Instant.now());

		estimateRepository.save(estimate);

		return buildPaymentInitResponse(
				estimate,
				payment,
				credentials,
				amountInSubunits,
				currency);
	}

	/*
	 * Do not make this entire method transactional.
	 *
	 * Razorpay verification happens first. The confirmed payment
	 * is then committed by EstimatePaymentSettlementService in
	 * its own REQUIRES_NEW transaction. Booking conversion runs
	 * afterward in another transaction.
	 */
	public EstimatePaymentStatusResponse verifyPayment(

			String rawToken,

			String orderId,

			String razorpayPaymentId,

			String signature) throws Exception {

		ResolvedEstimate resolved =
				resolve(rawToken);

		Estimate estimate =
				resolved.estimate();

		EstimateAccessToken token =
				resolved.token();

		Payment payment =
				paymentRepository
						.findByGatewayOrderIdAndEstimate_Id(
								orderId,
								estimate.getId())
						.orElseThrow(() ->
								new NotFoundException(
										ErrorCode.PAYMENT_NOT_FOUND,
										"Payment not found"));

		if (!estimate.getOrgId()
				.equals(payment.getOrgId())) {

			throw new BusinessException(
					ErrorCode.ACCESS_DENIED,
					"Payment organization mismatch");
		}

		/*
		 * Verification retries are idempotent.
		 * A confirmed payment retries conversion only.
		 */
		if (payment.getStatus()
				== PaymentStatus.CONFIRMED) {

			return convertConfirmedPayment(
					estimate,
					payment.getId(),
					estimate.getPaidAt());
		}

		RazorpayCredentials credentials =
				razorpayClientFactory.credentials(
						estimate.getOrgId());

		razorpayClientFactory
				.assertCheckoutEnabled(
						credentials);

		RazorpayClient razorpayClient =
				razorpayClientFactory.client(
						credentials);

		/*
		 * Verify the checkout response signature.
		 */
		razorpayClientFactory.verifySignature(
				credentials,
				orderId,
				razorpayPaymentId,
				signature);

		/*
		 * Fetch authoritative order and payment records.
		 */
		Order razorpayOrder =
				razorpayClient.orders.fetch(
						orderId);

		com.razorpay.Payment razorpayPayment =
				razorpayClient.payments.fetch(
						razorpayPaymentId);

		long expectedAmount =
				toSubunits(
						payment
								.getReceivedAmount()
								.getAmount());

		String expectedCurrency =
				payment
						.getReceivedAmount()
						.getCurrency()
						.name();

		validateRazorpayOrder(
				razorpayOrder,
				orderId,
				expectedAmount,
				expectedCurrency);

		validateRazorpayPaymentIdentity(
				razorpayPayment,
				razorpayPaymentId,
				orderId,
				expectedAmount,
				expectedCurrency);

		/*
		 * A successful checkout can initially return an
		 * authorised payment. Explicitly capture it before
		 * treating it as settled.
		 */
		razorpayPayment =
				captureIfAuthorized(
						razorpayClient,
						razorpayPayment,
						razorpayPaymentId,
						expectedAmount,
						expectedCurrency);

		validateCapturedPayment(
				razorpayPayment);

		String method =
				valueAsString(
						razorpayPayment,
						"method");

		Instant transactionDate =
				valueAsInstant(
						razorpayPayment,
						"created_at");

		/*
		 * Commit the captured payment independently from
		 * booking conversion.
		 */
		EstimatePaymentSettlementService
				.SettlementResult settlement =
				settlementService.confirmPayment(
						estimate.getId(),
						estimate.getOrgId(),
						token.getId(),
						orderId,
						razorpayPaymentId,
						signature,

						method == null
								? PaymentMode.UNKNOWN
								: PaymentUtil
								.mapPaymentMode(method),

						transactionDate);

		/*
		 * Conversion failure cannot roll back payment settlement.
		 */
		return convertConfirmedPayment(
				estimate,
				settlement.paymentId(),
				settlement.paidAt());
	}

	@Transactional
	public EstimatePaymentStatusResponse getPaymentStatus(
			String rawToken) {

		ResolvedEstimate resolved =
				resolve(rawToken);

		Estimate estimate =
				resolved.estimate();

		/*
		 * Prefer a confirmed payment over a newer pending attempt.
		 */
		Payment payment =
				paymentRepository
						.findTopByOrgIdAndEstimate_IdAndStatusInOrderByCreatedAtDesc(
								estimate.getOrgId(),
								estimate.getId(),
								List.of(
										PaymentStatus.CONFIRMED))
						.orElseGet(() ->
								paymentRepository
										.findTopByEstimate_IdOrderByCreatedAtDesc(
												estimate.getId())
										.orElse(null));

		PaymentStatus paymentStatus =
				payment == null
						? null
						: payment.getStatus();

		boolean paid =
				paymentStatus
						== PaymentStatus.CONFIRMED
						|| estimate.getStatus()
						== EstimateStatus.PAID
						|| estimate.getStatus()
						== EstimateStatus.CONVERTED;

		boolean converted =
				estimate.getConvertedBookingId()
						!= null
						&& !estimate
						.getConvertedBookingId()
						.isBlank();

		String message;

		if (converted) {

			message =
					"Booking confirmed";

		} else if (paid) {

			message =
					"Payment verified. Booking creation is pending";

		} else if (paymentStatus
				== PaymentStatus.INITIATED
				|| paymentStatus
				== PaymentStatus.PENDING) {

			message =
					"Payment pending";

		} else {

			message =
					"Payment not started";
		}

		return new EstimatePaymentStatusResponse(
				estimate.getStatus(),
				paymentStatus,
				paid,
				converted,
				estimate.getConvertedBookingId(),
				estimate.getPaidAt(),
				message);
	}

	public Money payableNow(
			Estimate estimate) {

		if (estimate.getAdvanceAmount()
				!= null
				&& estimate
				.getAdvanceAmount()
				.getAmount()
				!= null
				&& estimate
				.getAdvanceAmount()
				.getAmount()
				.compareTo(BigDecimal.ZERO)
				> 0) {

			return estimate.getAdvanceAmount();
		}

		return estimate.getEstimatedPayable();
	}

	private EstimatePaymentInitResponse
	tryReusePendingPayment(

			Estimate estimate,

			Payment payment,

			RazorpayClient razorpayClient,

			RazorpayCredentials credentials,

			long expectedAmount,

			String expectedCurrency)
			throws Exception {

		/*
		 * A malformed local payment cannot be reused.
		 */
		if (payment.getGateway()
				!= PaymentGateway.RAZORPAY
				|| payment.getGatewayOrderId()
				== null
				|| payment.getGatewayOrderId()
				.isBlank()
				|| payment.getReceivedAmount()
				== null
				|| payment
				.getReceivedAmount()
				.getAmount()
				== null
				|| payment
				.getReceivedAmount()
				.getCurrency()
				== null) {

			cancelPendingPayment(
					payment,
					"Discarded invalid pending payment record");

			return null;
		}

		long pendingAmount =
				toSubunits(
						payment
								.getReceivedAmount()
								.getAmount());

		String pendingCurrency =
				payment
						.getReceivedAmount()
						.getCurrency()
						.name();

		/*
		 * If the estimate was edited after order creation,
		 * discard the old local attempt.
		 */
		if (pendingAmount
				!= expectedAmount
				|| !expectedCurrency
				.equalsIgnoreCase(
						pendingCurrency)) {

			cancelPendingPayment(
					payment,
					"Discarded because estimate payable amount changed");

			return null;
		}

		final Order order;

		try {

			order = razorpayClient.orders.fetch(
					payment.getGatewayOrderId());

		} catch (RazorpayException exception) {

			log.error(
					"Unable to inspect existing Razorpay order {} for estimate {}",
					payment.getGatewayOrderId(),
					estimate.getEstimateId(),
					exception);

			/*
			 * Do not create another order when Razorpay's state
			 * cannot be established. That would create a
			 * duplicate-charge risk.
			 */
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Unable to check the existing Razorpay payment. Please retry shortly");
		}

		String orderStatus =
				normalized(
						valueAsString(
								order,
								"status"));

		long orderAmount =
				valueAsLong(
						order,
						"amount");

		String orderCurrency =
				valueAsString(
						order,
						"currency");

		if (orderAmount
				!= expectedAmount
				|| !expectedCurrency
				.equalsIgnoreCase(
						orderCurrency)) {

			cancelPendingPayment(
					payment,
					"Discarded because Razorpay order amount or currency changed");

			return null;
		}

		/*
		 * Both created and attempted orders remain payable.
		 */
		if ("created".equals(orderStatus)
				|| "attempted".equals(
				orderStatus)) {

			return buildPaymentInitResponse(
					estimate,
					payment,
					credentials,
					expectedAmount,
					expectedCurrency);
		}

		/*
		 * Never create another order when Razorpay already
		 * reports this one as paid.
		 */
		if ("paid".equals(orderStatus)) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Payment is already completed in Razorpay and is awaiting verification");
		}

		cancelPendingPayment(
				payment,
				"Discarded because Razorpay order is no longer payable. Status: "
						+ orderStatus);

		return null;
	}

	private EstimatePaymentInitResponse
	buildPaymentInitResponse(

			Estimate estimate,

			Payment payment,

			RazorpayCredentials credentials,

			long amount,

			String currency) {

		Client client =
				estimate.getClient();

		String clientName =
				client != null
						&& client.getName()
						!= null
						? client.getName()
						.getDisplayName()
						: null;

		return new EstimatePaymentInitResponse(

				credentials.keyId(),

				payment.getGatewayOrderId(),

				amount,

				currency,

				payment.getId(),

				new EstimatePaymentInitResponse
						.Prefill(

						clientName,

						client == null
								? null
								: client.getPhone(),

						client == null
								? null
								: client.getEmail()));
	}

	private EstimatePaymentStatusResponse
	convertConfirmedPayment(

			Estimate estimate,

			String paymentId,

			Instant paidAt) {

		try {

			Booking booking =
					estimateConversionService
							.convertAfterPayment(
									estimate.getId(),
									estimate.getOrgId(),
									paymentId);

			return new EstimatePaymentStatusResponse(

					EstimateStatus.CONVERTED,

					PaymentStatus.CONFIRMED,

					true,

					true,

					booking.getBookingId(),

					paidAt,

					"Payment verified and booking created");

		} catch (RuntimeException exception) {

			/*
			 * Payment remains confirmed because it was already
			 * committed in a separate transaction.
			 */
			log.error(
					"Payment {} was verified for estimate {}, but booking conversion failed",
					paymentId,
					estimate.getEstimateId(),
					exception);

			return new EstimatePaymentStatusResponse(

					EstimateStatus.PAID,

					PaymentStatus.CONFIRMED,

					true,

					false,

					null,

					paidAt,

					"Payment verified. Booking creation is pending");
		}
	}

	private void validateRazorpayOrder(

			Order order,

			String expectedOrderId,

			long expectedAmount,

			String expectedCurrency) {

		String actualOrderId =
				valueAsString(
						order,
						"id");

		long actualAmount =
				valueAsLong(
						order,
						"amount");

		String actualCurrency =
				valueAsString(
						order,
						"currency");

		if (!expectedOrderId.equals(
				actualOrderId)) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Razorpay order mismatch");
		}

		if (actualAmount
				!= expectedAmount) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Razorpay order amount mismatch");
		}

		if (!expectedCurrency
				.equalsIgnoreCase(
						actualCurrency)) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Razorpay order currency mismatch");
		}
	}

	private void validateRazorpayPaymentIdentity(

			com.razorpay.Payment razorpayPayment,

			String expectedPaymentId,

			String expectedOrderId,

			long expectedAmount,

			String expectedCurrency) {

		String actualPaymentId =
				valueAsString(
						razorpayPayment,
						"id");

		String actualOrderId =
				valueAsString(
						razorpayPayment,
						"order_id");

		long actualAmount =
				valueAsLong(
						razorpayPayment,
						"amount");

		String actualCurrency =
				valueAsString(
						razorpayPayment,
						"currency");

		if (!expectedPaymentId.equals(
				actualPaymentId)) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Razorpay payment mismatch");
		}

		if (!expectedOrderId.equals(
				actualOrderId)) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Razorpay payment order mismatch");
		}

		if (actualAmount
				!= expectedAmount) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Razorpay payment amount mismatch");
		}

		if (!expectedCurrency
				.equalsIgnoreCase(
						actualCurrency)) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Razorpay payment currency mismatch");
		}
	}

	private com.razorpay.Payment
	captureIfAuthorized(

			RazorpayClient razorpayClient,

			com.razorpay.Payment
					razorpayPayment,

			String paymentId,

			long amount,

			String currency)
			throws RazorpayException {

		String status =
				normalized(
						valueAsString(
								razorpayPayment,
								"status"));

		if (!"authorized".equals(
				status)) {

			return razorpayPayment;
		}

		JSONObject captureRequest =
				new JSONObject();

		captureRequest.put(
				"amount",
				amount);

		captureRequest.put(
				"currency",
				currency);

		return razorpayClient
				.payments
				.capture(
						paymentId,
						captureRequest);
	}

	private void validateCapturedPayment(
			com.razorpay.Payment razorpayPayment) {

		String status =
				normalized(
						valueAsString(
								razorpayPayment,
								"status"));

		boolean captured =
				valueAsBoolean(
						razorpayPayment,
						"captured");

		if (!"captured".equals(status)
				|| !captured) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Razorpay payment is not captured. Current status: "
							+ status);
		}
	}

	private void assertEstimateCanAcceptPayment(
			Estimate estimate) {

		boolean hasConfirmedPayment =
				paymentRepository
						.existsByOrgIdAndEstimate_IdAndStatus(
								estimate.getOrgId(),
								estimate.getId(),
								PaymentStatus.CONFIRMED);

		if (hasConfirmedPayment
				|| estimate.getStatus()
				== EstimateStatus.PAID
				|| estimate.getStatus()
				== EstimateStatus.CONVERTED) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Estimate is already paid");
		}

		if (estimate.getStatus()
				== EstimateStatus.CANCELLED
				|| estimate.getStatus()
				== EstimateStatus.EXPIRED
				|| estimate.getStatus()
				== EstimateStatus.REVOKED) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Estimate is not available for payment");
		}
	}

	private void validatePayableAmount(
			Money payableNow) {

		if (payableNow == null
				|| payableNow.getAmount()
				== null
				|| payableNow.getCurrency()
				== null
				|| payableNow
				.getAmount()
				.compareTo(BigDecimal.ZERO)
				<= 0) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"No payable amount found for this estimate");
		}
	}

	private void validateCurrency(

			Money payableNow,

			RazorpayCredentials credentials) {

		String estimateCurrency =
				payableNow
						.getCurrency()
						.name();

		String gatewayCurrency =
				credentials
						.resolvedCurrency();

		if (!estimateCurrency
				.equalsIgnoreCase(
						gatewayCurrency)) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Estimate currency does not match the configured Razorpay currency");
		}
	}

	private void cancelPendingPayment(

			Payment payment,

			String reason) {

		payment.setStatus(
				PaymentStatus.CANCELLED);

		payment.setRemarks(
				appendRemark(
						payment.getRemarks(),
						reason));

		paymentRepository.save(payment);
	}

	private String appendRemark(

			String existing,

			String addition) {

		if (existing == null
				|| existing.isBlank()) {

			return addition;
		}

		return existing
				+ " | "
				+ addition;
	}

	private long toSubunits(
			BigDecimal amount) {

		return amount
				.setScale(
						2,
						RoundingMode.HALF_UP)
				.multiply(
						BigDecimal.valueOf(100))
				.longValueExact();
	}

	private Money copyMoney(
			Money money) {

		return new Money(

				money.getAmount()
						.setScale(
								2,
								RoundingMode.HALF_UP),

				money.getCurrency());
	}

	private String normalized(
			String value) {

		return value == null
				? ""
				: value
				.trim()
				.toLowerCase(
						Locale.ROOT);
	}

	private String valueAsString(

			Object entity,

			String key) {

		Object value =
				valueOf(
						entity,
						key);

		return value == null
				? null
				: String.valueOf(value);
	}

	private long valueAsLong(

			Object entity,

			String key) {

		Object value =
				valueOf(
						entity,
						key);

		if (value instanceof Number number) {
			return number.longValue();
		}

		if (value == null) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Missing Razorpay field: "
							+ key);
		}

		return Long.parseLong(
				String.valueOf(value));
	}

	private boolean valueAsBoolean(

			Object entity,

			String key) {

		Object value =
				valueOf(
						entity,
						key);

		if (value instanceof Boolean
				booleanValue) {

			return booleanValue;
		}

		return value != null
				&& Boolean.parseBoolean(
				String.valueOf(value));
	}

	private Instant valueAsInstant(

			Object entity,

			String key) {

		Object value =
				valueOf(
						entity,
						key);

		if (value instanceof Number number) {

			return Instant.ofEpochSecond(
					number.longValue());
		}

		return Instant.now();
	}

	private Object valueOf(

			Object entity,

			String key) {

		if (entity instanceof Order order) {

			return order.get(key);
		}

		if (entity instanceof
				com.razorpay.Payment payment) {

			return payment.get(key);
		}

		throw new IllegalArgumentException(
				"Unsupported Razorpay entity");
	}

	private ResolvedEstimate resolve(
			String rawToken) {

		if (rawToken == null
				|| rawToken.isBlank()) {

			throw new BusinessException(
					ErrorCode.ACCESS_DENIED,
					"Invalid estimate link");
		}

		EstimateAccessToken token =
				tokenRepository
						.findByTokenHash(
								EstimateUtil.sha256(
										rawToken))
						.orElseThrow(() ->
								new BusinessException(
										ErrorCode.ACCESS_DENIED,
										"Invalid estimate link"));

		if (token.getStatus()
				== EstimateLinkStatus.REVOKED) {

			throw new BusinessException(
					ErrorCode.ACCESS_DENIED,
					"Estimate link has been revoked");
		}

		if (token.getStatus()
				== EstimateLinkStatus.EXPIRED
				|| token.getExpiresAt()
				.isBefore(Instant.now())) {

			token.setStatus(
					EstimateLinkStatus.EXPIRED);

			tokenRepository.save(token);

			throw new BusinessException(
					ErrorCode.ACCESS_DENIED,
					"Estimate link has expired");
		}

		Estimate estimate =
				estimateRepository
						.findById(
								token.getEstimateId())
						.orElseThrow(() ->
								new NotFoundException(
										ErrorCode.ESTIMATE_NOT_FOUND,
										"Estimate not found"));

		return new ResolvedEstimate(
				token,
				attachData(estimate));
	}

	private Estimate attachData(
			Estimate estimate) {

		if (estimate.getClientId()
				!= null) {

			estimate.setClient(
					clientService.get(
							estimate.getClientId(),
							estimate.getOrgId()));
		}

		return estimate;
	}

	private void initializePublicRelations(
			Estimate estimate) {

		if (estimate.getEntries()
				== null) {

			return;
		}

		for (EstimateEntry entry
				: estimate.getEntries()) {

			if (entry.getRequestedVehicle()
					!= null) {

				/*
				 * Accessing the proxy loads the full entity.
				 */
				entry.getRequestedVehicle()
						.getName();

				entry.getRequestedVehicle()
						.getPic();
			}

			if (entry.getCharges()
					!= null) {

				entry.getCharges().size();
			}
		}
	}

	private record ResolvedEstimate(

			EstimateAccessToken token,

			Estimate estimate) {
	}
}
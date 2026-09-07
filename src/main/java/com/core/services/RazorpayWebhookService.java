package com.core.services;

import java.util.Set;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.core.gateway.razerpay.RazorpayClientFactory;
import com.core.gateway.razerpay.RazorpayCredentials;
import com.core.gateway.razerpay.RazorpayPaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * Razorpay webhook consumer -- part of the P1B booking-checkout recovery
 * design (see the payment recovery audit). Only ever reached via
 * RazorpayWebhookController, whose path carries the org id as a fixed,
 * dashboard-configured URL segment (set once when an org registers its
 * webhook URL in its own Razorpay dashboard) -- never taken from the request
 * payload, so signature verification always resolves the correct per-org
 * secret before anything in the body is trusted.
 *
 * This class's only job is: verify the signature, then extract just enough
 * from the payload (the event type and the order id) to know WHICH order to
 * look at. It never trusts the payload's amount, currency or captured status
 * -- RazorpayPaymentService.reconcileByGatewayOrderId re-fetches and
 * re-validates all of that directly from Razorpay using our own stored
 * orderId.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookService {

	/*
	 * payment.captured is the direct, unambiguous "money is captured" event
	 * and carries a full payment entity -- exactly what
	 * reconcileByGatewayOrderId needs. Other webhook event types (order.paid,
	 * refund.*, etc.) are intentionally not acted on yet: acting on them
	 * correctly would need their own payload shapes modeled and tested, and
	 * this task's scope is booking-checkout capture recovery specifically.
	 * Unhandled events are acknowledged (200) and ignored, never treated as an
	 * error.
	 */
	private static final Set<String> HANDLED_EVENTS = Set.of("payment.captured");

	private final RazorpayClientFactory razorpayClientFactory;
	private final RazorpayPaymentService razorpayPaymentService;

	public void handle(String orgId, String rawBody, String signatureHeader) throws Exception {

		if (rawBody == null || rawBody.isBlank()) {
			throw new IllegalStateException("Empty Razorpay webhook body");
		}

		RazorpayCredentials credentials = razorpayClientFactory.credentials(orgId);

		// Throws SecurityException on a missing/invalid signature -- never parse
		// or act on the body before this succeeds.
		razorpayClientFactory.verifyWebhookSignature(credentials, rawBody, signatureHeader);

		JSONObject payload = new JSONObject(rawBody);
		String event = payload.optString("event", "");

		if (!HANDLED_EVENTS.contains(event)) {
			log.info("Razorpay webhook: ignoring unhandled event '{}' for org {}", event, orgId);
			return;
		}

		JSONObject paymentEntity = extractPaymentEntity(payload);

		if (paymentEntity == null) {
			throw new IllegalStateException("Razorpay webhook event '" + event + "' has no payment entity");
		}

		String orderId = paymentEntity.optString("order_id", null);

		if (orderId == null || orderId.isBlank()) {
			throw new IllegalStateException("Razorpay webhook event '" + event + "' has no order_id");
		}

		razorpayPaymentService.reconcileByGatewayOrderId(orgId, orderId);
	}

	private JSONObject extractPaymentEntity(JSONObject payload) {
		JSONObject payloadContainer = payload.optJSONObject("payload");

		if (payloadContainer == null) {
			return null;
		}

		JSONObject paymentContainer = payloadContainer.optJSONObject("payment");

		if (paymentContainer == null) {
			return null;
		}

		return paymentContainer.optJSONObject("entity");
	}
}

package com.core.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.gateway.razerpay.RazorpayClientFactory;
import com.core.gateway.razerpay.RazorpayCredentials;
import com.core.gateway.razerpay.RazorpayPaymentService;

/*
 * Covers RazorpayWebhookService's own responsibility only: verify the
 * signature, then extract the order id from a payment.captured payload and
 * hand off to RazorpayPaymentService.reconcileByGatewayOrderId --
 * RazorpayClientFactoryTest already covers the HMAC verification itself,
 * RazorpayPaymentServiceTest covers reconciliation correctness, so
 * RazorpayClientFactory/RazorpayPaymentService are mocked here.
 */
class RazorpayWebhookServiceTest {

	private static final String ORG_ID = "org-1";

	private RazorpayClientFactory clientFactory;
	private RazorpayPaymentService paymentService;
	private RazorpayWebhookService webhookService;
	private RazorpayCredentials credentials;

	@BeforeEach
	void setUp() {
		clientFactory = mock(RazorpayClientFactory.class);
		paymentService = mock(RazorpayPaymentService.class);
		webhookService = new RazorpayWebhookService(clientFactory, paymentService);

		credentials = new RazorpayCredentials(
				ORG_ID, "key_test", "secret_test", null, "INR", "Fleetovo", "Fleetovo", true, true, false, "whsec_test");
		when(clientFactory.credentials(ORG_ID)).thenReturn(credentials);
	}

	private String capturedEventPayload(String orderId) {
		return "{"
				+ "\"event\":\"payment.captured\","
				+ "\"payload\":{\"payment\":{\"entity\":{"
				+ "\"id\":\"pay_123\",\"order_id\":\"" + orderId + "\",\"amount\":100000,\"currency\":\"INR\"}}}"
				+ "}";
	}

	@Test
	void handle_reconciles_onValidCapturedEvent() throws Exception {
		String body = capturedEventPayload("order_abc");
		doNothing().when(clientFactory).verifyWebhookSignature(eq(credentials), eq(body), eq("sig"));

		webhookService.handle(ORG_ID, body, "sig");

		verify(paymentService, org.mockito.Mockito.times(1)).reconcileByGatewayOrderId(ORG_ID, "order_abc");
	}

	@Test
	void handle_rejects_invalidSignature_andNeverReconciles() throws Exception {
		String body = capturedEventPayload("order_abc");
		doThrow(new SecurityException("Invalid Razorpay webhook signature"))
				.when(clientFactory).verifyWebhookSignature(eq(credentials), eq(body), eq("bad-sig"));

		assertThrows(SecurityException.class, () -> webhookService.handle(ORG_ID, body, "bad-sig"));

		verify(paymentService, never()).reconcileByGatewayOrderId(any(), any());
	}

	@Test
	void handle_ignoresUnhandledEventTypes() throws Exception {
		String body = "{\"event\":\"order.paid\",\"payload\":{}}";
		doNothing().when(clientFactory).verifyWebhookSignature(eq(credentials), eq(body), eq("sig"));

		assertDoesNotThrow(() -> webhookService.handle(ORG_ID, body, "sig"));

		verify(paymentService, never()).reconcileByGatewayOrderId(any(), any());
	}

	@Test
	void handle_throws_whenCapturedEventHasNoOrderId() throws Exception {
		String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
		doNothing().when(clientFactory).verifyWebhookSignature(eq(credentials), eq(body), eq("sig"));

		assertThrows(IllegalStateException.class, () -> webhookService.handle(ORG_ID, body, "sig"));

		verify(paymentService, never()).reconcileByGatewayOrderId(any(), any());
	}

	@Test
	void handle_throws_whenPayloadHasNoPaymentEntity() throws Exception {
		String body = "{\"event\":\"payment.captured\",\"payload\":{}}";
		doNothing().when(clientFactory).verifyWebhookSignature(eq(credentials), eq(body), eq("sig"));

		assertThrows(IllegalStateException.class, () -> webhookService.handle(ORG_ID, body, "sig"));

		verify(paymentService, never()).reconcileByGatewayOrderId(any(), any());
	}

	@Test
	void handle_throws_onEmptyBody() {
		assertThrows(IllegalStateException.class, () -> webhookService.handle(ORG_ID, "", "sig"));
	}
}

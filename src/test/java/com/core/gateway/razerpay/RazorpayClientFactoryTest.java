package com.core.gateway.razerpay;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.core.services.PaymentGatewayConfigService;

/*
 * Covers the P1B webhook signature verification RazorpayWebhookService relies
 * on. computeHmac below independently reproduces Razorpay's documented
 * webhook signing scheme (HMAC-SHA256 of the raw body, hex-encoded) using
 * plain JDK crypto primitives -- it does not call the method under test, so
 * these assertions are not circular.
 */
class RazorpayClientFactoryTest {

	private final RazorpayClientFactory factory = new RazorpayClientFactory(mock(PaymentGatewayConfigService.class));

	private RazorpayCredentials credentialsWithWebhookSecret(String webhookSecret) {
		return new RazorpayCredentials(
				"org-1", "key_test", "secret_test", null, "INR", "Fleetovo", "Fleetovo",
				true, true, false, webhookSecret);
	}

	private String computeHmac(String secret, String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void verifyWebhookSignature_accepts_correctSignature() throws Exception {
		String body = "{\"event\":\"payment.captured\"}";
		String signature = computeHmac("whsec_abc", body);

		assertDoesNotThrow(() ->
				factory.verifyWebhookSignature(credentialsWithWebhookSecret("whsec_abc"), body, signature));
	}

	@Test
	void verifyWebhookSignature_rejects_tamperedBody() throws Exception {
		String originalBody = "{\"event\":\"payment.captured\"}";
		String signature = computeHmac("whsec_abc", originalBody);
		String tamperedBody = "{\"event\":\"payment.captured\",\"extra\":\"injected\"}";

		assertThrows(SecurityException.class, () ->
				factory.verifyWebhookSignature(credentialsWithWebhookSecret("whsec_abc"), tamperedBody, signature));
	}

	@Test
	void verifyWebhookSignature_rejects_wrongSecret() throws Exception {
		String body = "{\"event\":\"payment.captured\"}";
		String signature = computeHmac("whsec_abc", body);

		assertThrows(SecurityException.class, () ->
				factory.verifyWebhookSignature(credentialsWithWebhookSecret("whsec_different"), body, signature));
	}

	@Test
	void verifyWebhookSignature_rejects_whenNoWebhookSecretConfigured() {
		assertThrows(SecurityException.class, () ->
				factory.verifyWebhookSignature(credentialsWithWebhookSecret(null), "{}", "any-signature"));
	}

	@Test
	void verifyWebhookSignature_rejects_missingSignatureHeader() {
		assertThrows(SecurityException.class, () ->
				factory.verifyWebhookSignature(credentialsWithWebhookSecret("whsec_abc"), "{}", null));
	}

	@Test
	void verifyWebhookSignature_rejects_emptyBody() {
		assertThrows(SecurityException.class, () ->
				factory.verifyWebhookSignature(credentialsWithWebhookSecret("whsec_abc"), "", "some-signature"));
	}
}

package com.core.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.core.services.RazorpayWebhookService;

/*
 * Verifies the controller maps each outcome to the right HTTP status for
 * Razorpay's retry semantics: 2xx must never be returned for a signature
 * failure, and a permanent validation failure (already logged inside the
 * service) must be acknowledged (2xx) rather than retried forever, while an
 * unexpected/possibly-transient error asks Razorpay to retry (5xx). A plain
 * unit test (no Spring context), consistent with this suite's existing style.
 */
class RazorpayWebhookControllerTest {

	private final RazorpayWebhookService webhookService = mock(RazorpayWebhookService.class);
	private final RazorpayWebhookController controller = new RazorpayWebhookController(webhookService);

	@Test
	void handle_returnsOk_onSuccess() throws Exception {
		doNothing().when(webhookService).handle("org-1", "{}", "sig");

		ResponseEntity<String> response = controller.handle("org-1", "{}", "sig");

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	void handle_returnsBadRequest_onInvalidSignature() throws Exception {
		doThrow(new SecurityException("Invalid Razorpay webhook signature"))
				.when(webhookService).handle("org-1", "{}", "bad-sig");

		ResponseEntity<String> response = controller.handle("org-1", "{}", "bad-sig");

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	void handle_returnsOk_onDeterministicValidationFailure() throws Exception {
		doThrow(new IllegalStateException("Razorpay order amount does not match the expected payment amount"))
				.when(webhookService).handle("org-1", "{}", "sig");

		ResponseEntity<String> response = controller.handle("org-1", "{}", "sig");

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	void handle_returnsServerError_onUnexpectedFailure() throws Exception {
		doThrow(new RuntimeException("Razorpay API timeout"))
				.when(webhookService).handle("org-1", "{}", "sig");

		ResponseEntity<String> response = controller.handle("org-1", "{}", "sig");

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
}

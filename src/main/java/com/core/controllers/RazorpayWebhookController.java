package com.core.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.services.RazorpayWebhookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * Public by necessity -- only Razorpay's servers ever call this, and they
 * cannot present a Fleetovo JWT. Security instead comes entirely from the
 * HMAC signature check inside RazorpayWebhookService (see
 * SecurityConfiguration's permitAll entry for /webhooks/razorpay/** and
 * RazorpayClientFactory.verifyWebhookSignature).
 *
 * The org id in the path is fixed, dashboard-configured infrastructure (each
 * org registers its own https://.../webhooks/razorpay/{orgId} URL with its
 * own Razorpay account), not attacker-controlled request data -- it exists so
 * the correct per-org webhook secret can be resolved before anything in the
 * body is trusted.
 *
 * @RequestBody String binds the exact raw bytes Razorpay sent (Spring
 * resolves a String target to StringHttpMessageConverter), which is required
 * for HMAC verification -- parsing to a DTO first and re-serializing before
 * hashing would not reproduce the same bytes Razorpay signed.
 */
@RestController
@RequestMapping("/webhooks/razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

	private final RazorpayWebhookService webhookService;

	@PostMapping("/{orgId}")
	public ResponseEntity<String> handle(
			@PathVariable String orgId,
			@RequestBody String rawBody,
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
	) {
		try {
			webhookService.handle(orgId, rawBody, signature);
			return ResponseEntity.ok("ok");

		} catch (SecurityException ex) {
			log.warn("Razorpay webhook rejected for org {}: {}", orgId, ex.getMessage());
			return ResponseEntity.badRequest().body("rejected");

		} catch (IllegalStateException ex) {
			/*
			 * A deterministic validation failure (identity/amount/currency
			 * mismatch, malformed payload, unconfigured org). Already logged
			 * inside the service at WARN/ERROR as appropriate. Retrying will not
			 * change a deterministic outcome, so acknowledge receipt (2xx)
			 * rather than let Razorpay retry this delivery forever.
			 */
			log.warn("Razorpay webhook for org {} could not be applied: {}", orgId, ex.getMessage());
			return ResponseEntity.ok("acknowledged");

		} catch (Exception ex) {
			/*
			 * Possibly transient (Razorpay API timeout, a DB hiccup while
			 * reconciling) -- ask Razorpay to retry with a 5xx.
			 */
			log.error("Razorpay webhook processing failed for org {}", orgId, ex);
			return ResponseEntity.internalServerError().body("error");
		}
	}
}

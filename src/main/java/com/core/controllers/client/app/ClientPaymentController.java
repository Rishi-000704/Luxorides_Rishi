package com.core.controllers.client.app;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.core.gateway.PaymentOrderDTO;
import com.core.gateway.VerifyPaymentDTO;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayCheckoutPayload;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Client;
import com.core.models.enums.PaymentGateway;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;
import com.core.services.PaymentGatewayConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/client/app/payments")
@RequiredArgsConstructor
public class ClientPaymentController {

	private final RazorpayPaymentService razorpayPaymentService;
	private final MockPaymentService mockPaymentService;
	private final PaymentGatewayConfigService paymentGatewayConfigService;
	private final SecurityContextUtil security;
	private final ClientService clientService;

	/* ================= CREATE PAYMENT ORDER ================= */

	@PostMapping("/order")
	@PreAuthorize("isAuthenticated()")
	public Object createOrder(@RequestBody PaymentOrderDTO request) throws Exception {

		if (request.gateway() == null) {
			throw new IllegalArgumentException("Payment gateway is required");
		}

		// P0 IDOR fix -- the caller's own Client identity is what proves
		// ownership, never the bookingId supplied in the request body.
		// Resolved once here and passed down so both gateways enforce it
		// before doing any provider work.
		Client client = clientService.findByUserId(security.userId());

		return switch (resolveEffectiveGateway(request.gateway())) {

		case RAZORPAY -> {
			RazorpayCheckoutPayload payload = razorpayPaymentService.createRazorpayOrder(request.bookingId(),
					client.getId(), security.orgId());
			yield payload;
		}

		case MOCK -> mockPaymentService.createMockOrder(request.bookingId(), client.getId(), security.orgId());

		default -> throw new UnsupportedOperationException("Unsupported payment gateway: " + request.gateway());
		};
	}

	/* ================= VERIFY PAYMENT ================= */

	@PostMapping("/verify")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<?> verifyPayment(@RequestBody VerifyPaymentDTO request) throws Exception {

		if (request.gateway() == null) {
			throw new IllegalArgumentException("Payment gateway is required");
		}

		switch (resolveEffectiveGateway(request.gateway())) {

		case RAZORPAY -> razorpayPaymentService.verifyPayment(security.orgId(), request.bookingId(), request.orderId(),
				request.paymentId(), request.signature());

		case MOCK -> mockPaymentService.confirmMockOrder(security.orgId(), request.bookingId(), request.orderId());

		default -> throw new UnsupportedOperationException("Unsupported payment gateway: " + request.gateway());
		}

		return ResponseEntity.ok(Map.of("status", "SUCCESS", "bookingId", request.bookingId()));
	}

	/*
	 * The frontend always requests RAZORPAY (there's no gateway picker in the UI) --
	 * the org's actual configured gateway is what really decides, same as it would for
	 * any other multi-gateway setup. This is what lets a MOCK PaymentGatewayConfig row
	 * (see DevDataSeeder) redirect local dev traffic to the dummy gateway with zero
	 * frontend request changes.
	 *
	 * P-hardening -- an org with no config row at all used to fall back to whatever
	 * gateway the CLIENT requested, which meant any authenticated customer could POST
	 * gateway=MOCK directly (bypassing the frontend, which never offers a picker) and
	 * get MockPaymentService to mark their own booking CONFIRMED with zero real money
	 * movement, for any org that simply hadn't configured payments yet. Payment success
	 * must never be a client-chosen fact. An unconfigured org now always resolves to
	 * RAZORPAY regardless of what was requested -- real orgs' frontend already only ever
	 * requests RAZORPAY, so this changes nothing for them, while a non-RAZORPAY request
	 * against an unconfigured org now safely fails (missing Razorpay credentials) instead
	 * of silently succeeding for free. Only an explicit, org-scoped MOCK config row (e.g.
	 * DevDataSeeder) can still route to MockPaymentService.
	 */
	private PaymentGateway resolveEffectiveGateway(PaymentGateway requested) {
		return paymentGatewayConfigService.getRuntimeConfig(security.orgId())
				.map(config -> config.gateway())
				.orElseGet(() -> {
					if (requested != PaymentGateway.RAZORPAY) {
						log.warn(
								"Org {} has no PaymentGatewayConfig; ignoring client-requested gateway {} and defaulting to RAZORPAY",
								security.orgId(), requested);
					}
					return PaymentGateway.RAZORPAY;
				});
	}
}

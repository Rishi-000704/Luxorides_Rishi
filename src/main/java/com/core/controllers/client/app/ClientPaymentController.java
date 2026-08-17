package com.core.controllers.client.app;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.core.gateway.PaymentOrderDTO;
import com.core.gateway.VerifyPaymentDTO;
import com.core.gateway.razerpay.RazorpayCheckoutPayload;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.security.SecurityContextUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/client/app/payments")
@RequiredArgsConstructor
public class ClientPaymentController {

	private final RazorpayPaymentService razorpayPaymentService;
	private final SecurityContextUtil security;

	/* ================= CREATE PAYMENT ORDER ================= */

	@PostMapping("/order")
	@PreAuthorize("isAuthenticated()")
	public Object createOrder(@RequestBody PaymentOrderDTO request) throws Exception {

		if (request.gateway() == null) {
			throw new IllegalArgumentException("Payment gateway is required");
		}

		return switch (request.gateway()) {

		case RAZORPAY -> {
			RazorpayCheckoutPayload payload = razorpayPaymentService.createRazorpayOrder(request.bookingId(),
					security.orgId());
			yield payload;
		}

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

		switch (request.gateway()) {

		case RAZORPAY -> razorpayPaymentService.verifyPayment(security.orgId(), request.bookingId(), request.orderId(),
				request.paymentId(), request.signature());

		default -> throw new UnsupportedOperationException("Unsupported payment gateway: " + request.gateway());
		}

		return ResponseEntity.ok(Map.of("status", "SUCCESS", "bookingId", request.bookingId()));
	}
}

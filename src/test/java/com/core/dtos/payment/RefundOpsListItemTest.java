package com.core.dtos.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/*
 * Regression guard for Phase 4 of the refund recovery audit ("Do NOT expose
 * Razorpay secrets, webhook secrets, API keys, raw authentication tokens,
 * full payment gateway payloads"): locks the Ops list response down to an
 * explicit allowlist of fields, so a future edit that adds a field pulled
 * straight off PaymentGatewayConfig/RazorpayCredentials would fail this test
 * rather than silently leaking into an API response.
 */
class RefundOpsListItemTest {

	private static final Set<String> ALLOWED_FIELDS = Set.of(
			"id", "bookingId", "customerName", "customerPhone",
			"paidAmount", "feeAmount", "refundAmount", "status",
			"gatewayRefundId", "failureReason", "reviewedBy", "reviewedAt", "createdAt");

	@Test
	void containsOnlyTheExplicitlyAllowedFields() {
		List<String> actual = Arrays.stream(RefundOpsListItem.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertEquals(ALLOWED_FIELDS.size(), actual.size());
		assertTrue(ALLOWED_FIELDS.containsAll(actual));
	}

	@Test
	void neverExposesGatewaySecretShapedFields() {
		for (RecordComponent component : RefundOpsListItem.class.getRecordComponents()) {
			String lower = component.getName().toLowerCase(java.util.Locale.ROOT);
			assertTrue(!lower.contains("secret"), "Unexpected secret-shaped field: " + component.getName());
			assertTrue(!lower.contains("apikey") && !lower.contains("api_key"),
					"Unexpected API key field: " + component.getName());
			assertTrue(!lower.contains("webhook"), "Unexpected webhook field: " + component.getName());
			assertTrue(!lower.contains("token"), "Unexpected token field: " + component.getName());
			assertTrue(!lower.contains("password"), "Unexpected password field: " + component.getName());
		}
	}
}

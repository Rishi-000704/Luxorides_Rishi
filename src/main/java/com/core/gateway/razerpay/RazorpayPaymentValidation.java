package com.core.gateway.razerpay;

/*
 * Shared provider-identity validation for any code path that confirms a
 * checkout payment WITHOUT a client-supplied, already-verified signature --
 * i.e. the webhook and scheduled-reconciliation recovery paths (see
 * RazorpayPaymentService.reconcileByGatewayOrderId). The signature-based path
 * (verifyPayment) proves the browser saw a genuine Razorpay response; these
 * paths instead prove correctness by independently re-fetching the order and
 * its payments from Razorpay using our own stored orderId and re-validating
 * identity, amount, currency and captured state -- never trusting a webhook
 * payload or a bare payment id on its own.
 *
 * Deliberately a fresh, independent implementation rather than an extraction
 * of PublicEstimateService's private validateRazorpayOrder/
 * validateRazorpayPaymentIdentity/validateCapturedPayment methods: those are
 * proven, tested, financially-critical code for the Estimate flow, and this
 * change must not risk altering their behavior. com.razorpay.Entity (the
 * common base of both Order and Payment) already exposes a single generic
 * get(String), so unlike that older code this needs no per-type dispatch.
 */
public final class RazorpayPaymentValidation {

	private RazorpayPaymentValidation() {
	}

	public static void validateOrderIdentity(
			com.razorpay.Entity order,
			String expectedOrderId,
			long expectedAmountInSubunits,
			String expectedCurrency
	) {
		String actualOrderId = stringField(order, "id");

		if (!expectedOrderId.equals(actualOrderId)) {
			throw new IllegalStateException("Razorpay order id does not match the expected order");
		}

		long actualAmount = longField(order, "amount");

		if (actualAmount != expectedAmountInSubunits) {
			throw new IllegalStateException("Razorpay order amount does not match the expected payment amount");
		}

		String actualCurrency = stringField(order, "currency");

		if (!expectedCurrency.equalsIgnoreCase(actualCurrency)) {
			throw new IllegalStateException("Razorpay order currency does not match the expected payment currency");
		}
	}

	public static void validatePaymentIdentity(
			com.razorpay.Entity payment,
			String expectedOrderId,
			long expectedAmountInSubunits,
			String expectedCurrency
	) {
		String actualOrderId = stringField(payment, "order_id");

		if (!expectedOrderId.equals(actualOrderId)) {
			throw new IllegalStateException("Razorpay payment is not linked to the expected order");
		}

		long actualAmount = longField(payment, "amount");

		if (actualAmount != expectedAmountInSubunits) {
			throw new IllegalStateException("Razorpay payment amount does not match the expected payment amount");
		}

		String actualCurrency = stringField(payment, "currency");

		if (!expectedCurrency.equalsIgnoreCase(actualCurrency)) {
			throw new IllegalStateException("Razorpay payment currency does not match the expected payment currency");
		}
	}

	public static boolean isCaptured(com.razorpay.Entity payment) {
		String status = normalized(stringField(payment, "status"));
		boolean captured = booleanField(payment, "captured");

		return captured || "captured".equals(status);
	}

	private static String normalized(String value) {
		return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static String stringField(com.razorpay.Entity entity, String key) {
		Object value = entity.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private static long longField(com.razorpay.Entity entity, String key) {
		Object value = entity.get(key);

		if (value instanceof Number number) {
			return number.longValue();
		}

		throw new IllegalStateException("Missing or invalid Razorpay field: " + key);
	}

	private static boolean booleanField(com.razorpay.Entity entity, String key) {
		Object value = entity.get(key);

		if (value instanceof Boolean booleanValue) {
			return booleanValue;
		}

		return value != null && Boolean.parseBoolean(String.valueOf(value));
	}
}

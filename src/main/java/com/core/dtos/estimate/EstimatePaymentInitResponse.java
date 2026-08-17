package com.core.dtos.estimate;

public record EstimatePaymentInitResponse(
		String key,
		String orderId,
		long amount,
		String currency,
		String paymentId,
		Prefill prefill) {

	public record Prefill(String name, String contact, String email) {
	}
}
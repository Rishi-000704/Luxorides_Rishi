package com.core.dtos.estimate;

public record EstimatePaymentVerifyRequest(
		String razorpayOrderId,
		String razorpayPaymentId,
		String razorpaySignature) {
}
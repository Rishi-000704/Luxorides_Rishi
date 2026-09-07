package com.core.models.enums;

public enum RefundRequestStatus {
	PENDING_REVIEW,
	APPROVED,
	REJECTED,
	COMPLETED,
	FAILED,

	/*
	 * The Razorpay refund call itself succeeded (we have a real gatewayRefundId)
	 * but recording that locally failed partway through -- e.g. a DB error right
	 * after the provider call returned. Deliberately distinct from FAILED: FAILED
	 * means "nothing happened, this is safe to reconsider"; this means "money has
	 * already moved, do not issue another refund for this request without first
	 * checking Razorpay for gatewayRefundId". See RefundRequestService.approve.
	 */
	COMPLETED_NEEDS_VERIFICATION
}

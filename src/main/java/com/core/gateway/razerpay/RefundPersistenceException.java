package com.core.gateway.razerpay;

/*
 * Thrown by RazorpayPaymentService.refundPayment specifically when the
 * Razorpay refund call itself succeeded (a real gatewayRefundId exists) but
 * recording that locally failed afterward. Distinguishing this from an
 * ordinary refund failure lets RefundRequestService.approve preserve the
 * refund id and mark the request in a way that can never be safely
 * re-approved into a duplicate real refund -- see
 * RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION.
 */
public class RefundPersistenceException extends RuntimeException {

	private final String refundId;

	public RefundPersistenceException(String refundId, Throwable cause) {
		super("Razorpay refund " + refundId + " succeeded but local persistence failed: "
				+ (cause == null ? "unknown error" : cause.getMessage()), cause);
		this.refundId = refundId;
	}

	public String getRefundId() {
		return refundId;
	}
}

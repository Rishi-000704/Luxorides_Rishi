package com.core.dtos.payment;

import java.math.BigDecimal;
import java.time.Instant;

import com.core.models.enums.RefundRequestStatus;

/*
 * Ops-facing refund recovery view (RefundController's paginated /page
 * endpoint). Deliberately a projection, not the raw RefundRequest entity:
 * carries a customer reference an operator needs (name/phone -- the same
 * fields already shown elsewhere in Fleetovo's existing booking/invoice
 * screens, not a new PII exposure) that the entity itself has no relation to
 * fetch, and deliberately omits orgId (implicit/redundant for an
 * already-org-scoped response) and anything gateway-secret-shaped -- only an
 * opaque gatewayRefundId reference is ever included, never a key/secret/
 * webhook value or raw provider payload.
 */
public record RefundOpsListItem(
		String id,
		String bookingId,
		String customerName,
		String customerPhone,
		BigDecimal paidAmount,
		BigDecimal feeAmount,
		BigDecimal refundAmount,
		RefundRequestStatus status,
		String gatewayRefundId,
		String failureReason,
		String reviewedBy,
		Instant reviewedAt,
		Instant createdAt
) {
}

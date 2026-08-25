package com.core.models;

import java.math.BigDecimal;
import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.RefundRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Real money movement (a Razorpay refund call) only ever happens once an
 * employee explicitly approves one of these rows -- see
 * RefundInitiatedListener (creates PENDING_REVIEW) and RefundRequestService
 * (approve() is the only place that calls RazorpayPaymentService.refundPayment).
 * Created for every cancellation, employee- or client-initiated alike, so
 * there is exactly one place refunds are ever decided.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "refund_request")
public class RefundRequest extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String bookingId;

	@Column(precision = 12, scale = 2)
	private BigDecimal paidAmount;

	@Column(precision = 12, scale = 2)
	private BigDecimal feeAmount;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal refundAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RefundRequestStatus status;

	@Column(length = 100)
	private String gatewayRefundId;

	@Column(length = 500)
	private String failureReason;

	private String reviewedBy;

	private Instant reviewedAt;
}

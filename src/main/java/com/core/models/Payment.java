package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.Money;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentMode;
import com.core.models.enums.PaymentStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
/*
 * P1.7 -- idx_payment_reconciliation added specifically for
 * DutyPaymentReconciliationJob's fixedDelay=7000 query
 * (findAllByCollectionContextAndStatusAndGatewayAndExpiresAtAfter), which
 * runs forever and had no supporting index on any of its four filter
 * columns. Column order matches the query's own equality predicates
 * (collection_context, status, gateway) before its one range predicate
 * (expires_at), the standard composite-index ordering for this shape of
 * query. A plain (non-unique) index -- safe to add regardless of existing
 * data, unlike a uniqueness constraint.
 */
@Table(name = "payment", indexes = {
		@Index(name = "idx_payment_booking", columnList = "booking_id"),
		@Index(name = "idx_payment_invoice", columnList = "invoice_id"),
		@Index(name = "idx_payment_estimate", columnList = "estimate_id"),
		@Index(name = "idx_payment_reconciliation", columnList = "collection_context, status, gateway, expires_at")
})
public class Payment extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;
	
	@Column(nullable = false, length = 40)
	private String orgId;

	/* ---------------- Ownership ---------------- */

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invoice_id")
	private Invoice invoice;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "estimate_id")
	private Estimate estimate;

	/* ---------------- Financial data ---------------- */

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentMode paymentMode;

	@Column(length = 50)
	private String transactionNumber;

	private Instant transactionDate;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "received_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "received_currency", length = 3, nullable = false)) })
	private Money receivedAmount;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "tds_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "tds_currency", length = 3, nullable = false)) })
	private Money tds;

	@Column(length = 500)
	private String remarks;

	/* ---------------- Gateway ---------------- */

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentGateway gateway; // RAZORPAY, CASH, BANK_TRANSFER

	@Column(length = 100)
	private String gatewayOrderId; // Razorpay order_id

	@Column(length = 100)
	private String gatewayPaymentId; // Razorpay payment_id

	@Column(length = 200)
	private String gatewaySignature;
	
	@Column(length = 100)
	private String gatewayQrCodeId;
	
	@Column(name = "gateway_qr_image_url", length = 500)
	private String gatewayQrImageUrl;

	@Column(length = 500)
	private String gatewayQrImageContent;

	@Column(length = 50)
	private String collectionContext;
	// DRIVER_DUTY_QR

	@Column(length = 80)
	private String collectionContextId;
	// dutyId

	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;
}

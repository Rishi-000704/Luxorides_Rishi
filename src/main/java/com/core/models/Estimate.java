package com.core.models;

import java.time.Instant;
import java.util.List;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.core.models.enums.EstimateStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Estimate extends AuditableEntity {
	@Id
	@Column(length = 40)
	private String id;

	@Column(length = 40)
	private String estimateId;
	@Column(nullable = false, length = 40)
	private String orgId;
	private Instant estimateDate;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EstimateStatus status;

	private Instant validTill;
	private Instant sentAt;
	private Instant viewedAt;
	private Instant paymentInitiatedAt;
	private Instant paidAt;
	private Instant convertedAt;

	@Column(length = 40)
	private String convertedBookingId;
	@Column(nullable = false, length = 40)
	private String clientId;
	@Column(length = 40)
	private String clientBillingEntityId;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "subtotal_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "subtotal_currency", length = 3, nullable = false)) })
	private Money subtotal;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "discount_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "discount_currency", length = 3, nullable = false)) })
	private Money discount;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "taxable_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "taxable_currency", length = 3, nullable = false)) })
	private Money taxableAmount;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "gstType", column = @Column(name = "gst_type", length = 20, nullable = false)),
			@AttributeOverride(name = "gstRate", column = @Column(name = "gst_rate")),
			@AttributeOverride(name = "igstAmount", column = @Column(name = "gst_igst_amount", precision = 15, scale = 2)),
			@AttributeOverride(name = "cgstAmount", column = @Column(name = "gst_cgst_amount", precision = 15, scale = 2)),
			@AttributeOverride(name = "sgstAmount", column = @Column(name = "gst_sgst_amount", precision = 15, scale = 2)),
			@AttributeOverride(name = "totalTax", column = @Column(name = "gst_total_tax", precision = 15, scale = 2, nullable = false)) })
	private GstSnapshot gstSnapshot;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "payable_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "payable_currency", length = 3, nullable = false)) })
	private Money estimatedPayable;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "advance_amount", precision = 15, scale = 2)),
			@AttributeOverride(name = "currency", column = @Column(name = "advance_currency", length = 3)) })
	private Money advanceAmount;

	@Column(length = 500)
	private String remarks;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	List<EstimateEntry> entries;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clientId", referencedColumnName = "id", insertable = false, updatable = false)
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clientBillingEntityId", referencedColumnName = "id", insertable = false, updatable = false)
	private ClientBillingEntity clientBillingEntity;

}

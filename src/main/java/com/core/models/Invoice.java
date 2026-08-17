package com.core.models;

import java.time.Instant;
import java.util.List;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.InvoiceStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "invoice",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_invoice_org_invoice_number",
						columnNames = {"org_id", "invoice_number"}
				),
				@UniqueConstraint(
						name = "uk_invoice_org_booking",
						columnNames = {"org_id", "booking_id"}
				)
		}
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Invoice extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(length = 40)
	private String clientId;

	@Column(length = 40)
	private String clientBillingEntityId;

	@Column(length = 40)
	private String orgBillingEntityId;

	@Column(nullable = false, length = 50)
	private String invoiceNumber;
	
	@Column(nullable = false, length = 50)
	private String bookingId;

	private Instant invoiceDate;

	@Column(nullable = false, length = 20)
	private String placeOfSupply;

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
			@AttributeOverride(name = "amount", column = @Column(name = "grand_total_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "grand_total_currency", length = 3, nullable = false)) })
	private Money  grandTotal;
	
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "total_paid_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "total_paid_currency", length = 3, nullable = false)) })
	private Money  totalPaid;
	
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "balance_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "balance_currency", length = 3, nullable = false)) })
	private Money  balanceAmount;

	@OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<InvoiceEntry> entries;

	@OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Payment> payments;

	@Column(length = 500)
	private String remarks;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InvoiceStatus status;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clientId", referencedColumnName = "id", insertable = false, updatable = false)
	private Client client;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clientBillingEntityId", referencedColumnName = "id", insertable = false, updatable = false)
	private ClientBillingEntity clientBillingEntity;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "orgBillingEntityId", referencedColumnName = "id", insertable = false, updatable = false)
	private OrgBillingEntity orgBillingEntity;
}

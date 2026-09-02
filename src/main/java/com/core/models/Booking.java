package com.core.models;

import java.util.ArrayList;
import java.util.List;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * P1.1 -- indexes added against verified repository query evidence, not
 * guessed. See BookingRepository:
 *   idx_booking_org_bookingid : findByBookingIdAndOrgId / lockByBookingIdAndOrgId
 *                               (18 call sites across payment, invoice, refund,
 *                               driver-duty and notification code -- the single
 *                               hottest Booking lookup in the codebase).
 *   idx_booking_org_client    : findByClientIdAndOrgId (customer app's "My
 *                               Bookings" list, BookingService.getClientBookings).
 *   idx_booking_org_status    : searchBookingsByStatus /
 *                               searchBookingsOrderByFirstDutyReportingTimeAsc/Desc
 *                               (ops app booking board) and the fraud-signal
 *                               repeated-cancellations query. org_id leads in
 *                               all three because every query here always
 *                               scopes by org first, matching this codebase's
 *                               existing composite-index convention (see
 *                               ReportRequest/PurchaseInvoice).
 */
@Entity
@Table(name = "booking", indexes = {
		@Index(name = "idx_booking_org_bookingid", columnList = "org_id, booking_id"),
		@Index(name = "idx_booking_org_client", columnList = "org_id, client_id"),
		@Index(name = "idx_booking_org_status", columnList = "org_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booking extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 36, updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String bookingId;
	
	@Column(length = 40)
	private String sourceEstimateId;

	@Column(length = 50)
	private String invoiceNumber;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String clientId;

	@Column(length = 40)
	private String clientBillingEntityId;

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
			@AttributeOverride(name = "amount", column = @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "total_currency", length = 3, nullable = false)) })
	private Money total;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "discount_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "discount_currency", length = 3, nullable = false))
	})
	private Money discount;

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<BookingEntry> entries = new ArrayList<BookingEntry>();

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Payment> payments = new ArrayList<Payment>();

	@Column(length = 500)
	private String remarks;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookingStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clientId", referencedColumnName = "id", insertable = false, updatable = false)
	private Client client;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clientBillingEntityId", referencedColumnName = "id", insertable = false, updatable = false)
	private ClientBillingEntity clientBillingEntity;

}

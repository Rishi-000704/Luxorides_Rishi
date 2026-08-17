package com.core.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "purchase_invoice_entry",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_purchase_invoice_entry_active_booking_entry",
                        columnNames = "active_booking_entry_id"
                )
        },
        indexes = {
                @Index(name = "idx_purchase_invoice_entry_invoice", columnList = "purchase_invoice_id"),
                @Index(name = "idx_purchase_invoice_entry_org_vendor", columnList = "orgId,vendorId"),
                @Index(name = "idx_purchase_invoice_entry_booking_entry", columnList = "bookingEntryId"),
                @Index(name = "idx_purchase_invoice_entry_active_booking_entry", columnList = "active_booking_entry_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseInvoiceEntry extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 40)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_invoice_id", nullable = false)
    private PurchaseInvoice purchaseInvoice;

    @Column(nullable = false, length = 40)
    private String orgId;

    @Column(nullable = false, length = 40)
    private String vendorId;

    @Column(nullable = false, length = 40)
    private String bookingId;

    /**
     * Permanent audit reference to BookingEntry.
     * Never clear this value.
     */
    @Column(nullable = false, length = 40)
    private String bookingEntryId;

    /**
     * Live duplicate-prevention lock.
     *
     * DRAFT / COMPLETED / PAID:
     * activeBookingEntryId = bookingEntryId
     *
     * CANCELLED:
     * activeBookingEntryId = null
     */
    @Column(name = "active_booking_entry_id", length = 40)
    private String activeBookingEntryId;

    @Column(nullable = false, length = 40)
    private String dutyId;

    /**
     * Purchase package snapshot.
     *
     * IMPORTANT:
     * PackageSnapshot already contains Money fields with column names like:
     * base_fare_amount, extra_per_km_amount, night_charge_amount, etc.
     *
     * PurchaseInvoiceEntry also has its own amount fields like nightCharge.
     * Therefore every PackageSnapshot column is explicitly prefixed with "pack_"
     * to avoid duplicate Hibernate column mappings.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "packageId",
                    column = @Column(name = "pack_package_id", length = 40)
            ),
            @AttributeOverride(
                    name = "scope",
                    column = @Column(name = "pack_scope", length = 20)
            ),
            @AttributeOverride(
                    name = "dutyType",
                    column = @Column(name = "pack_duty_type", length = 20)
            ),
            @AttributeOverride(
                    name = "time",
                    column = @Column(name = "pack_time")
            ),
            @AttributeOverride(
                    name = "distance",
                    column = @Column(name = "pack_distance")
            ),
            @AttributeOverride(
                    name = "unit",
                    column = @Column(name = "pack_unit", length = 20)
            ),

            @AttributeOverride(
                    name = "baseFare.amount",
                    column = @Column(name = "pack_base_fare_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "baseFare.currency",
                    column = @Column(name = "pack_base_fare_currency", length = 3)
            ),

            @AttributeOverride(
                    name = "extraPerKM.amount",
                    column = @Column(name = "pack_extra_per_km_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "extraPerKM.currency",
                    column = @Column(name = "pack_extra_per_km_currency", length = 3)
            ),

            @AttributeOverride(
                    name = "extraPerHS.amount",
                    column = @Column(name = "pack_extra_per_hs_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "extraPerHS.currency",
                    column = @Column(name = "pack_extra_per_hs_currency", length = 3)
            ),

            @AttributeOverride(
                    name = "nightCharge.amount",
                    column = @Column(name = "pack_night_charge_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "nightCharge.currency",
                    column = @Column(name = "pack_night_charge_currency", length = 3)
            )
    })
    private PackageSnapshot pack;

    @Column(length = 40)
    private String masterVehicleId;

    private Instant reportingTime;

    private Instant dropTime;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "formattedAddress",
                    column = @Column(name = "reporting_address", length = 300)
            ),
            @AttributeOverride(
                    name = "googlePlaceId",
                    column = @Column(name = "reporting_place_id", length = 100)
            ),
            @AttributeOverride(
                    name = "latitude",
                    column = @Column(name = "reporting_latitude")
            ),
            @AttributeOverride(
                    name = "longitude",
                    column = @Column(name = "reporting_longitude")
            )
    })
    private AddressSnapshot reportingLocation;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "formattedAddress",
                    column = @Column(name = "drop_address", length = 300)
            ),
            @AttributeOverride(
                    name = "googlePlaceId",
                    column = @Column(name = "drop_place_id", length = 100)
            ),
            @AttributeOverride(
                    name = "latitude",
                    column = @Column(name = "drop_latitude")
            ),
            @AttributeOverride(
                    name = "longitude",
                    column = @Column(name = "drop_longitude")
            )
    })
    private AddressSnapshot dropLocation;

    private Integer startingKM;

    private Integer closingKM;

    private Instant startAt;

    private Instant endAt;

    @Column(length = 10)
    private String flightNumber;

    @Column(length = 40)
    private String supplierId;

    @Column(length = 40)
    private String fleetVehicleId;

    @Column(length = 40)
    private String driverId;

    private Integer runningDays;

    private Integer extraChargebleDistance;

    private Float extraChargebleTime;

    private Boolean nightChargeble;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "chargeble_base_fare_amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "chargeble_base_fare_currency", length = 3, nullable = false)
            )
    })
    private Money chargebleBaseFare;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "extra_distance_charge_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "extra_distance_charge_currency", length = 3)
            )
    })
    private Money extraChargeDistance;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "extra_time_charge_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "extra_time_charge_currency", length = 3)
            )
    })
    private Money extraChargeTime;

    /**
     * Actual purchase invoice night charge amount.
     * This is separate from pack_night_charge_amount, which is only the package-rate snapshot.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "night_charge_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "night_charge_currency", length = 3)
            )
    })
    private Money nightCharge;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "extra_charges_total_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "extra_charges_total_currency", length = 3)
            )
    })
    private Money extraChargesTotal;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "duty_total_amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "duty_total_currency", length = 3, nullable = false)
            )
    })
    private Money dutyTotal;

    @OneToMany(
            mappedBy = "purchaseInvoiceEntry",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PurchaseInvoiceExtraCharge> charges = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "purchase_invoice_entry_passengers",
            joinColumns = @JoinColumn(name = "purchase_invoice_entry_id")
    )
    @Column(name = "passenger_id", length = 40)
    private List<String> passengerIds = new ArrayList<>();

    @Column(length = 500)
    private String clientNotes;

    @Column(length = 500)
    private String remarks;

    public void lockDuty() {
        this.activeBookingEntryId = this.bookingEntryId;
    }

    public void releaseDutyLock() {
        this.activeBookingEntryId = null;
    }

    public void addCharge(PurchaseInvoiceExtraCharge charge) {
        charge.setPurchaseInvoiceEntry(this);
        this.charges.add(charge);
    }
}
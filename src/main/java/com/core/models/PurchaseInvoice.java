package com.core.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.PurchaseInvoiceStatus;

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

@Entity
@Table(
        name = "purchase_invoice",
        indexes = {
                @Index(name = "idx_purchase_invoice_org_vendor", columnList = "orgId,vendorId"),
                @Index(name = "idx_purchase_invoice_org_status", columnList = "orgId,status"),
                @Index(name = "idx_purchase_invoice_invoice_date", columnList = "orgId,invoiceDate")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseInvoice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 40)
    private String orgId;

    @Column(nullable = false, length = 40)
    private String vendorId;

    @Column(nullable = false, length = 40)
    private String vendorBillingEntityId;

    @Column(nullable = false, length = 40)
    private String orgBillingEntityId;

    @Column(nullable = false, length = 50)
    private String purchaseInvoiceNumber;

    @Column(length = 100)
    private String vendorInvoiceNumber;

    private Instant vendorInvoiceDate;

    @Column(nullable = false)
    private Instant invoiceDate;

    private Instant dueDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "subtotal_amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "subtotal_currency", length = 3, nullable = false)
            )
    })
    private Money subtotal;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "taxable_amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "taxable_currency", length = 3, nullable = false)
            )
    })
    private Money taxableAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "gstType",
                    column = @Column(name = "gst_type", length = 20, nullable = false)
            ),
            @AttributeOverride(
                    name = "gstRate",
                    column = @Column(name = "gst_rate")
            ),
            @AttributeOverride(
                    name = "igstAmount",
                    column = @Column(name = "gst_igst_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "cgstAmount",
                    column = @Column(name = "gst_cgst_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "sgstAmount",
                    column = @Column(name = "gst_sgst_amount", precision = 15, scale = 2)
            ),
            @AttributeOverride(
                    name = "totalTax",
                    column = @Column(name = "gst_total_tax", precision = 15, scale = 2, nullable = false)
            )
    })
    private GstSnapshot gstSnapshot;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "grand_total_amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "grand_total_currency", length = 3, nullable = false)
            )
    })
    private Money grandTotal;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "paid_amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "paid_currency", length = 3, nullable = false)
            )
    })
    private Money paidAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "balance_amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "balance_currency", length = 3, nullable = false)
            )
    })
    private Money balanceAmount;

    @Column(length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseInvoiceStatus status;

    @OneToMany(
            mappedBy = "purchaseInvoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<PurchaseInvoiceEntry> entries = new ArrayList<>();

    @OneToMany(
            mappedBy = "purchaseInvoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<PaymentOut> payments = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendorId", referencedColumnName = "id", insertable = false, updatable = false)
    private Client vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendorBillingEntityId", referencedColumnName = "id", insertable = false, updatable = false)
    private ClientBillingEntity vendorBillingEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orgBillingEntityId", referencedColumnName = "id", insertable = false, updatable = false)
    private OrgBillingEntity orgBillingEntity;

    public void addEntry(PurchaseInvoiceEntry entry) {
        entry.setPurchaseInvoice(this);
        this.entries.add(entry);
    }

    public void addPayment(PaymentOut paymentOut) {
        paymentOut.setPurchaseInvoice(this);
        this.payments.add(paymentOut);
    }
}
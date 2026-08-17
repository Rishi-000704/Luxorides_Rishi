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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "payment_out",
        indexes = {
                @Index(name = "idx_payment_out_org_vendor", columnList = "orgId,vendorId"),
                @Index(name = "idx_payment_out_invoice", columnList = "purchase_invoice_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOut extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 40)
    private String orgId;

    @Column(nullable = false, length = 40)
    private String vendorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id")
    private PurchaseInvoice purchaseInvoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMode paymentMode;

    @Column(length = 50)
    private String transactionNumber;

    private Instant transactionDate;

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
                    column = @Column(name = "tds_amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "tds_currency", length = 3, nullable = false)
            )
    })
    private Money tds;

    @Column(length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentGateway gateway;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;
}
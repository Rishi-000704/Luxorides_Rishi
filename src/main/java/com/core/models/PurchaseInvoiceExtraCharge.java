package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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
        name = "purchase_invoice_extra_charge",
        indexes = {
                @Index(name = "idx_purchase_invoice_extra_charge_entry", columnList = "purchase_invoice_entry_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseInvoiceExtraCharge extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 40)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_invoice_entry_id", nullable = false)
    private PurchaseInvoiceEntry purchaseInvoiceEntry;

    @Column(length = 200)
    private String description;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "amount",
                    column = @Column(name = "amount", precision = 15, scale = 2, nullable = false)
            ),
            @AttributeOverride(
                    name = "currency",
                    column = @Column(name = "currency", length = 3, nullable = false)
            )
    })
    private Money amount;
}
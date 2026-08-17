package com.core.models;

import com.core.models.embedded.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoice_extra_charge")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceExtraCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 40)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_entry_id", nullable = false)
    private InvoiceEntry invoiceEntry;

    @Column(length = 200)
    private String description;

    @Column(length = 255)
    private String image;

    @Embedded
    private Money amount;
}
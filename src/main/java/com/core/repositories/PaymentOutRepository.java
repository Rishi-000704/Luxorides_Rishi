package com.core.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.PaymentOut;
import com.core.models.enums.PaymentStatus;

@Repository
public interface PaymentOutRepository extends JpaRepository<PaymentOut, String> {

    List<PaymentOut> findByOrgIdAndVendorIdAndStatus(
            String orgId,
            String vendorId,
            PaymentStatus status
    );

    List<PaymentOut> findByOrgIdAndPurchaseInvoiceIdAndStatus(
            String orgId,
            String purchaseInvoiceId,
            PaymentStatus status
    );

    List<PaymentOut> findByOrgIdAndTransactionDateBetweenAndStatus(
            String orgId,
            Instant from,
            Instant till,
            PaymentStatus status
    );
}
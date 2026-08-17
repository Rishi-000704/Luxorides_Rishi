package com.core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.core.models.PurchaseInvoiceEntry;

public interface PurchaseInvoiceEntryRepository extends JpaRepository<PurchaseInvoiceEntry, String> {

    boolean existsByActiveBookingEntryId(String activeBookingEntryId);

    List<PurchaseInvoiceEntry> findByPurchaseInvoiceId(String purchaseInvoiceId);

    @Query("""
			SELECT e.activeBookingEntryId
			FROM PurchaseInvoiceEntry e
			WHERE e.activeBookingEntryId IN :bookingEntryIds
			""")
    List<String> findActiveBookingEntryIds(
            @Param("bookingEntryIds") List<String> bookingEntryIds
    );

    @Query("""
			SELECT e
			FROM PurchaseInvoiceEntry e
			LEFT JOIN FETCH e.charges
			WHERE e.purchaseInvoice.id = :purchaseInvoiceId
			ORDER BY e.reportingTime ASC
			""")
    List<PurchaseInvoiceEntry> findDetailedByPurchaseInvoiceId(
            @Param("purchaseInvoiceId") String purchaseInvoiceId
    );
}
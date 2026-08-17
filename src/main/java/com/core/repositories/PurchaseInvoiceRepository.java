package com.core.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.PurchaseInvoice;
import com.core.models.enums.PurchaseInvoiceStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, String> {

	Optional<PurchaseInvoice> findByIdAndOrgId(String id, String orgId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
            SELECT pi
            FROM PurchaseInvoice pi
            LEFT JOIN FETCH pi.entries e
            WHERE pi.id = :id
              AND pi.orgId = :orgId
            """)
	Optional<PurchaseInvoice> lockByIdAndOrgId(
			@Param("id") String id,
			@Param("orgId") String orgId
	);

	boolean existsByOrgIdAndVendorIdAndVendorInvoiceNumberAndStatusNot(
			String orgId,
			String vendorId,
			String vendorInvoiceNumber,
			PurchaseInvoiceStatus status
	);

	@Query("""
            SELECT pi
            FROM PurchaseInvoice pi
            LEFT JOIN FETCH pi.vendor
            LEFT JOIN FETCH pi.vendorBillingEntity
            LEFT JOIN FETCH pi.orgBillingEntity
            WHERE pi.id = :id
              AND pi.orgId = :orgId
            """)
	Optional<PurchaseInvoice> findDetailedByIdAndOrgId(
			@Param("id") String id,
			@Param("orgId") String orgId
	);

	@Query(
			value = """
                    SELECT pi
                    FROM PurchaseInvoice pi
                    LEFT JOIN FETCH pi.vendor v
                    WHERE pi.orgId = :orgId
                      AND (:vendorId IS NULL OR pi.vendorId = :vendorId)
                      AND (:status IS NULL OR pi.status = :status)
                      AND (
                           :searchstr = ''
                           OR LOWER(pi.purchaseInvoiceNumber) LIKE LOWER(CONCAT('%', :searchstr, '%'))
                           OR LOWER(pi.vendorInvoiceNumber) LIKE LOWER(CONCAT('%', :searchstr, '%'))
                           OR LOWER(CONCAT(
                               COALESCE(v.name.salutation, ''), ' ',
                               COALESCE(v.name.firstName, ''), ' ',
                               COALESCE(v.name.lastName, '')
                           )) LIKE LOWER(CONCAT('%', :searchstr, '%'))
                      )
                    """,
			countQuery = """
                    SELECT COUNT(pi)
                    FROM PurchaseInvoice pi
                    LEFT JOIN pi.vendor v
                    WHERE pi.orgId = :orgId
                      AND (:vendorId IS NULL OR pi.vendorId = :vendorId)
                      AND (:status IS NULL OR pi.status = :status)
                      AND (
                           :searchstr = ''
                           OR LOWER(pi.purchaseInvoiceNumber) LIKE LOWER(CONCAT('%', :searchstr, '%'))
                           OR LOWER(pi.vendorInvoiceNumber) LIKE LOWER(CONCAT('%', :searchstr, '%'))
                           OR LOWER(CONCAT(
                               COALESCE(v.name.salutation, ''), ' ',
                               COALESCE(v.name.firstName, ''), ' ',
                               COALESCE(v.name.lastName, '')
                           )) LIKE LOWER(CONCAT('%', :searchstr, '%'))
                      )
                    """
	)
	Page<PurchaseInvoice> getPage(
			@Param("orgId") String orgId,
			@Param("vendorId") String vendorId,
			@Param("status") PurchaseInvoiceStatus status,
			@Param("searchstr") String searchstr,
			Pageable pageable
	);

	@Query("""
            SELECT pi
            FROM PurchaseInvoice pi
            LEFT JOIN FETCH pi.vendor
            LEFT JOIN FETCH pi.vendorBillingEntity
            LEFT JOIN FETCH pi.orgBillingEntity
            WHERE pi.orgId = :orgId
              AND pi.orgBillingEntityId = :orgBillingEntityId
              AND pi.status IN :statuses
              AND pi.invoiceDate >= :from
              AND pi.invoiceDate < :toExclusive
            ORDER BY pi.invoiceDate ASC, pi.purchaseInvoiceNumber ASC
            """)
	List<PurchaseInvoice> findReportInvoices(
			@Param("orgId") String orgId,
			@Param("orgBillingEntityId") String orgBillingEntityId,
			@Param("statuses") Set<PurchaseInvoiceStatus> statuses,
			@Param("from") Instant from,
			@Param("toExclusive") Instant toExclusive
	);
}

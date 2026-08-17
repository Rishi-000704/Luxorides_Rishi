package com.core.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.dtos.invoice.ClientPendingListItem;
import com.core.models.Invoice;
import com.core.models.enums.InvoiceStatus;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

	Optional<Invoice> findInvoiceByInvoiceNumberAndOrgId(String invoiceNumber, String orgId);

	Optional<Invoice> findByBookingIdAndOrgId(String bookingId, String orgId);

	@Query("""
		SELECT DISTINCT i
		FROM Invoice i
		JOIN FETCH i.client c
		LEFT JOIN FETCH i.clientBillingEntity cbe
		LEFT JOIN FETCH i.orgBillingEntity obe
		WHERE i.orgId = :orgId
		  AND i.orgBillingEntityId = :orgBillingEntityId
		  AND i.status IN :statuses
		  AND i.invoiceDate >= :from
		  AND i.invoiceDate < :to
		ORDER BY i.invoiceDate ASC, i.invoiceNumber ASC
		""")
	List<Invoice> fetchInvoicesForReport(@Param("orgId") String orgId,
	                                     @Param("orgBillingEntityId") String orgBillingEntityId,
	                                     @Param("statuses") java.util.Set<InvoiceStatus> statuses,
	                                     @Param("from") Instant from,
	                                     @Param("to") Instant to);

	@Query("""
			SELECT DISTINCT i
			FROM Invoice i
			LEFT JOIN i.client c
			LEFT JOIN i.clientBillingEntity cbe
			WHERE i.orgId = :orgId
			  AND (
			       :status IS NULL
			       OR i.status = :status
			  )
			  AND (
			       :search IS NULL
			       OR :search = ''
			       OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(i.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(i.remarks) LIKE LOWER(CONCAT('%', :search, '%'))

			       OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))

			       OR LOWER(cbe.legalName) LIKE LOWER(CONCAT('%', :search, '%'))
			  )
			""")
	Page<Invoice> searchInvoicesByStatus(@Param("orgId") String orgId, @Param("status") InvoiceStatus status,
	                                     @Param("search") String search, Pageable pageable);

	@Query("""
			    SELECT new com.core.dtos.invoice.ClientPendingListItem(

			        c.id,

			        CONCAT(
			            COALESCE(c.name.firstName, ''),
			            ' ',
			            COALESCE(c.name.lastName, '')
			        ),

			        c.phone,

			        c.email,

			        SUM(i.balanceAmount.amount)

			    )

			    FROM Invoice i
			    JOIN i.client c

			    WHERE i.orgId = :orgId

			      AND i.status = com.core.models.enums.InvoiceStatus.ISSUED

			      AND i.balanceAmount.amount > 0

			      AND (
			            :search IS NULL
			            OR :search = ''

			            OR LOWER(c.name.firstName)
			                LIKE LOWER(CONCAT('%', :search, '%'))

			            OR LOWER(c.name.lastName)
			                LIKE LOWER(CONCAT('%', :search, '%'))

			            OR LOWER(c.phone)
			                LIKE LOWER(CONCAT('%', :search, '%'))

			            OR LOWER(c.email)
			                LIKE LOWER(CONCAT('%', :search, '%'))
			          )

			    GROUP BY
			        c.id,
			        c.name.firstName,
			        c.name.lastName,
			        c.phone,
			        c.email

			    ORDER BY
			        SUM(i.balanceAmount.amount) DESC
			""")
	Page<ClientPendingListItem> getClientPendingPage(@Param("orgId") String orgId, @Param("search") String search,
	                                                 Pageable pageable);

	@Query(
			value = """
                SELECT i
                FROM Invoice i
                JOIN FETCH i.client c
                LEFT JOIN FETCH i.clientBillingEntity cbe
                WHERE i.orgId = :orgId
                  AND i.clientId = :clientId
                  AND (:status IS NULL OR i.status = :status)
                  AND (
                        :search IS NULL
                     OR :search = ''
                     OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(i.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(i.remarks) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(cbe.legalName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
                """,
			countQuery = """
                SELECT COUNT(i)
                FROM Invoice i
                LEFT JOIN i.clientBillingEntity cbe
                WHERE i.orgId = :orgId
                  AND i.clientId = :clientId
                  AND (:status IS NULL OR i.status = :status)
                  AND (
                        :search IS NULL
                     OR :search = ''
                     OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(i.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(i.remarks) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(cbe.legalName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
                """
	)
	Page<Invoice> searchClientInvoices(
			@Param("orgId") String orgId,
			@Param("clientId") String clientId,
			@Param("status") InvoiceStatus status,
			@Param("search") String search,
			Pageable pageable
	);

}

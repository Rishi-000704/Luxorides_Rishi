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

import com.core.models.Booking;
import com.core.models.enums.BookingStatus;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {

	/*
	 * ------------------------------------------------- Core lookups
	 * -------------------------------------------------
	 */

	/*
	 * Real historical booking-creation timestamps for DemandForecastService --
	 * bucketed in Java (hour-of-day / day-of-week), not via a DB-specific
	 * date function, to stay portable. Fleet-operator booking volume is small
	 * enough that fetching raw timestamps for a bounded window is fine.
	 */
	@Query("SELECT b.createdAt FROM Booking b WHERE b.orgId = :orgId AND b.createdAt >= :since")
	List<Instant> findCreatedAtSince(@Param("orgId") String orgId, @Param("since") Instant since);

	/*
	 * Real anomaly-detection aggregates for FraudSignalService -- HAVING
	 * COUNT(...) >= :threshold does the real thresholding in SQL, not a
	 * fabricated/guessed severity.
	 */
	@Query("""
		SELECT b.clientId, COUNT(b)
		FROM Booking b
		WHERE b.orgId = :orgId AND b.createdAt >= :since AND b.clientId IS NOT NULL
		GROUP BY b.clientId
		HAVING COUNT(b) >= :threshold
	""")
	List<Object[]> findClientsWithRapidBookings(
			@Param("orgId") String orgId, @Param("since") Instant since, @Param("threshold") long threshold);

	@Query("""
		SELECT b.clientId, COUNT(b)
		FROM Booking b
		WHERE b.orgId = :orgId AND b.status = com.core.models.enums.BookingStatus.CANCELLED
		  AND b.updatedAt >= :since AND b.clientId IS NOT NULL
		GROUP BY b.clientId
		HAVING COUNT(b) >= :threshold
	""")
	List<Object[]> findClientsWithRepeatedCancellations(
			@Param("orgId") String orgId, @Param("since") Instant since, @Param("threshold") long threshold);

	Optional<Booking> findByBookingIdAndOrgId(String bookingId, String orgId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT b
		FROM Booking b
		WHERE b.bookingId = :bookingId
		  AND b.orgId = :orgId
		""")
	Optional<Booking> lockByBookingIdAndOrgId(
			@Param("bookingId") String bookingId,
			@Param("orgId") String orgId
	);

	@Query("""
		    SELECT DISTINCT b
		    FROM Booking b
		    LEFT JOIN b.entries e
		    LEFT JOIN b.client c
		    LEFT JOIN e.requestedVehicle mv
		    WHERE b.orgId = :orgId
		      AND (
		            :status IS NULL
		            OR b.status = :status
		          )
		      AND (
		            :search IS NULL
		            OR :search = ''
		            OR
		            LOWER(b.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))
		            OR LOWER(b.remarks) LIKE LOWER(CONCAT('%', :search, '%'))

		            OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
		            OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))

		            OR LOWER(mv.name) LIKE LOWER(CONCAT('%', :search, '%'))
		          )
		""")
	Page<Booking> searchBookingsByStatus(@Param("orgId") String orgId, @Param("status") BookingStatus status,
			@Param("search") String search, Pageable pageable);

	List<Booking> findByClientIdAndOrgId(String clientId, String orgId);
	
	  @Query("""
		        select case when count(be) = 0 then true else false end
		        from BookingEntry be
		        where be.booking.bookingId = :bookingId
		          and be.status <> com.core.models.enums.DutyStatus.COMPLETED
		    """)
		    boolean areAllDutiesCompleted(@Param("bookingId") String bookingId);

	@Query(
			value = """
			SELECT b
			FROM Booking b
			LEFT JOIN b.entries sortEntry
			LEFT JOIN b.entries searchEntry
			LEFT JOIN b.client c
			LEFT JOIN searchEntry.requestedVehicle mv
			WHERE b.orgId = :orgId
			  AND (
			        :status IS NULL
			        OR b.status = :status
			      )
			  AND (
			        :search IS NULL
			        OR :search = ''
			        OR LOWER(b.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(b.remarks) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(mv.name) LIKE LOWER(CONCAT('%', :search, '%'))
			      )
			GROUP BY b
			ORDER BY
			  CASE WHEN MIN(sortEntry.reportingTime) IS NULL THEN 1 ELSE 0 END ASC,
			  MIN(sortEntry.reportingTime) ASC,
			  b.createdAt DESC
		""",
			countQuery = """
			SELECT COUNT(DISTINCT b)
			FROM Booking b
			LEFT JOIN b.entries searchEntry
			LEFT JOIN b.client c
			LEFT JOIN searchEntry.requestedVehicle mv
			WHERE b.orgId = :orgId
			  AND (
			        :status IS NULL
			        OR b.status = :status
			      )
			  AND (
			        :search IS NULL
			        OR :search = ''
			        OR LOWER(b.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(b.remarks) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(mv.name) LIKE LOWER(CONCAT('%', :search, '%'))
			      )
		"""
	)
	Page<Booking> searchBookingsOrderByFirstDutyReportingTimeAsc(
			@Param("orgId") String orgId,
			@Param("status") BookingStatus status,
			@Param("search") String search,
			Pageable pageable
	);

	@Query(
			value = """
			SELECT b
			FROM Booking b
			LEFT JOIN b.entries sortEntry
			LEFT JOIN b.entries searchEntry
			LEFT JOIN b.client c
			LEFT JOIN searchEntry.requestedVehicle mv
			WHERE b.orgId = :orgId
			  AND (
			        :status IS NULL
			        OR b.status = :status
			      )
			  AND (
			        :search IS NULL
			        OR :search = ''
			        OR LOWER(b.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(b.remarks) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(mv.name) LIKE LOWER(CONCAT('%', :search, '%'))
			      )
			GROUP BY b
			ORDER BY
			  CASE WHEN MIN(sortEntry.reportingTime) IS NULL THEN 1 ELSE 0 END ASC,
			  MIN(sortEntry.reportingTime) DESC,
			  b.createdAt DESC
		""",
			countQuery = """
			SELECT COUNT(DISTINCT b)
			FROM Booking b
			LEFT JOIN b.entries searchEntry
			LEFT JOIN b.client c
			LEFT JOIN searchEntry.requestedVehicle mv
			WHERE b.orgId = :orgId
			  AND (
			        :status IS NULL
			        OR b.status = :status
			      )
			  AND (
			        :search IS NULL
			        OR :search = ''
			        OR LOWER(b.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(b.remarks) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(mv.name) LIKE LOWER(CONCAT('%', :search, '%'))
			      )
		"""
	)
	Page<Booking> searchBookingsOrderByFirstDutyReportingTimeDesc(
			@Param("orgId") String orgId,
			@Param("status") BookingStatus status,
			@Param("search") String search,
			Pageable pageable
	);
}

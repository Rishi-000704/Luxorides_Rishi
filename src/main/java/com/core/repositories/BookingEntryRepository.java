package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.BookingEntry;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface BookingEntryRepository extends JpaRepository<BookingEntry, String> {
	
	Optional<BookingEntry> findByDutyId(String dutyId);

	/* -------------------------------------------------
	   Safe operational lookup (allotment / close)
	   ------------------------------------------------- */

	@Query("""
		SELECT e
		FROM BookingEntry e
		WHERE e.id = :entryId
		  AND e.booking.bookingId = :bookingId
		  AND e.booking.orgId = :orgId
	""")
	Optional<BookingEntry> findForOperation(
		@Param("orgId") String orgId,
		@Param("bookingId") String bookingId,
		@Param("entryId") String entryId
	);

	/* -------------------------------------------------
	   Pessimistic lock (optional but recommended)
	   ------------------------------------------------- */

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT e
		FROM BookingEntry e
		WHERE e.id = :entryId
	""")
	Optional<BookingEntry> lockById(@Param("entryId") String entryId);

	/* -------------------------------------------------
	   Booking-scoped queries
	   ------------------------------------------------- */

	List<BookingEntry> findByBooking_BookingId(String bookingId);

	boolean existsByBooking_BookingIdAndStatusNot(
		String bookingId,
		DutyStatus status
	);

	/* -------------------------------------------------
	   Availability checks
	   ------------------------------------------------- */

	boolean existsByFleetVehicleIdAndStatusIn(
		String fleetVehicleId,
		List<DutyStatus> statuses
	);

	boolean existsByDriverIdAndStatusIn(
		String driverId,
		List<DutyStatus> statuses
	);
	
	/* -------------------------------------------------
	   Driver Link
	   ------------------------------------------------- */
	
	@Query("""
			SELECT e
			FROM BookingEntry e
			JOIN FETCH e.booking b
			LEFT JOIN FETCH e.driver d
			LEFT JOIN FETCH e.allotedVehicle fv
			LEFT JOIN FETCH fv.masterVehicle mv
			WHERE e.dutyId = :dutyId
			  AND b.orgId = :orgId
		""")
		Optional<BookingEntry> findByDutyIdAndOrgId(
			@Param("dutyId") String dutyId,
			@Param("orgId") String orgId
		);

		@Lock(LockModeType.PESSIMISTIC_WRITE)
		@Query("""
			SELECT e
			FROM BookingEntry e
			JOIN FETCH e.booking b
			WHERE e.dutyId = :dutyId
			  AND b.orgId = :orgId
		""")
		Optional<BookingEntry> lockByDutyIdAndOrgId(
			@Param("dutyId") String dutyId,
			@Param("orgId") String orgId
		);
		
		@Query("""
				SELECT e
				FROM BookingEntry e
				JOIN FETCH e.booking b
				LEFT JOIN FETCH e.driver d
				LEFT JOIN FETCH e.allotedVehicle av
				LEFT JOIN FETCH av.masterVehicle mv
				WHERE b.orgId = :orgId
				  AND b.bookingId = :bookingId
				  AND e.dutyId = :dutyId
			""")
			Optional<BookingEntry> findForDriverDutySubmissionView(
				@Param("orgId") String orgId,
				@Param("bookingId") String bookingId,
				@Param("dutyId") String dutyId
			);
		
		@Query(
				value = """
					SELECT e
					FROM BookingEntry e
					JOIN FETCH e.booking b
					LEFT JOIN FETCH b.client c
					LEFT JOIN FETCH e.requestedVehicle rv
					LEFT JOIN FETCH e.allotedVehicle fv
					LEFT JOIN FETCH fv.masterVehicle fvmv
					LEFT JOIN FETCH e.driver d
					LEFT JOIN FETCH e.supplier s
					WHERE b.orgId = :orgId

					  AND (
					       :status IS NULL
					       OR e.status = :status
					  )

					  AND (
					       :bookingStatus IS NULL
					       OR b.status = :bookingStatus
					  )

					  AND (
					       :bookingId IS NULL
					       OR :bookingId = ''
					       OR b.bookingId = :bookingId
					  )

					  AND (
					       :search IS NULL
					       OR :search = ''

					       OR LOWER(e.dutyId) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(b.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(rv.name) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(fv.registrationNumber) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(fvmv.name) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(d.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(d.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(d.phone) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(s.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(s.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(e.flightNumber) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(e.reportingLocation.formattedAddress) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(e.dropLocation.formattedAddress) LIKE LOWER(CONCAT('%', :search, '%'))
					  )
				""",
				countQuery = """
					SELECT COUNT(e)
					FROM BookingEntry e
					JOIN e.booking b
					LEFT JOIN b.client c
					LEFT JOIN e.requestedVehicle rv
					LEFT JOIN e.allotedVehicle fv
					LEFT JOIN fv.masterVehicle fvmv
					LEFT JOIN e.driver d
					LEFT JOIN e.supplier s
					WHERE b.orgId = :orgId

					  AND (
					       :status IS NULL
					       OR e.status = :status
					  )

					  AND (
					       :bookingStatus IS NULL
					       OR b.status = :bookingStatus
					  )

					  AND (
					       :bookingId IS NULL
					       OR :bookingId = ''
					       OR b.bookingId = :bookingId
					  )

					  AND (
					       :search IS NULL
					       OR :search = ''

					       OR LOWER(e.dutyId) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(b.bookingId) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(rv.name) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(fv.registrationNumber) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(fvmv.name) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(d.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(d.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(d.phone) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(s.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(s.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))

					       OR LOWER(e.flightNumber) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(e.reportingLocation.formattedAddress) LIKE LOWER(CONCAT('%', :search, '%'))
					       OR LOWER(e.dropLocation.formattedAddress) LIKE LOWER(CONCAT('%', :search, '%'))
					  )
				"""
			)
			Page<BookingEntry> searchDuties(
				@Param("orgId") String orgId,
				@Param("status") DutyStatus status,
				@Param("bookingStatus") BookingStatus bookingStatus,
				@Param("bookingId") String bookingId,
				@Param("search") String search,
				Pageable pageable
			);

		/* -------------------------------------------------
   Purchase invoice helpers
   ------------------------------------------------- */

	@Query("""
	SELECT e
	FROM BookingEntry e
	JOIN FETCH e.booking b
	LEFT JOIN FETCH b.client
	LEFT JOIN FETCH e.requestedVehicle
	LEFT JOIN FETCH e.allotedVehicle
	LEFT JOIN FETCH e.driver
	WHERE b.orgId = :orgId
	  AND e.supplierId = :vendorId
	  AND e.status = :status
	ORDER BY e.reportingTime ASC
""")
	List<BookingEntry> findVendorCompletedDutiesForPurchase(
			@Param("orgId") String orgId,
			@Param("vendorId") String vendorId,
			@Param("status") DutyStatus status
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
	SELECT e
	FROM BookingEntry e
	JOIN FETCH e.booking b
	LEFT JOIN FETCH b.client
	LEFT JOIN FETCH e.charges
	WHERE e.id = :entryId
	  AND b.orgId = :orgId
""")
	Optional<BookingEntry> lockByIdAndOrgId(
			@Param("entryId") String entryId,
			@Param("orgId") String orgId
	);
}

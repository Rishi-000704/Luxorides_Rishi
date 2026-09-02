package com.core.repositories;

import java.time.Instant;
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
	   Driver self-service dashboard (driver/app)
	   ------------------------------------------------- */

	@Query("""
			SELECT e
			FROM BookingEntry e
			JOIN FETCH e.booking b
			LEFT JOIN FETCH b.client
			LEFT JOIN FETCH e.allotedVehicle fv
			LEFT JOIN FETCH fv.masterVehicle
			WHERE b.orgId = :orgId
			  AND e.driverId = :driverId
			  AND e.status IN :statuses
			  AND b.status IN :bookingStatuses
			ORDER BY e.reportingTime ASC
		""")
	Page<BookingEntry> findActiveDutiesForDriver(
		@Param("orgId") String orgId,
		@Param("driverId") String driverId,
		@Param("statuses") List<DutyStatus> statuses,
		@Param("bookingStatuses") List<BookingStatus> bookingStatuses,
		Pageable pageable
	);

	@Query("""
			SELECT e
			FROM BookingEntry e
			JOIN FETCH e.booking b
			LEFT JOIN FETCH b.client
			LEFT JOIN FETCH e.allotedVehicle fv
			LEFT JOIN FETCH fv.masterVehicle
			WHERE b.orgId = :orgId
			  AND e.driverId = :driverId
			  AND e.status = :status
			ORDER BY e.dropTime DESC
		""")
	Page<BookingEntry> findCompletedDutiesForDriver(
		@Param("orgId") String orgId,
		@Param("driverId") String driverId,
		@Param("status") DutyStatus status,
		Pageable pageable
	);

	@Query("""
			SELECT e
			FROM BookingEntry e
			JOIN FETCH e.booking b
			LEFT JOIN FETCH b.client
			LEFT JOIN FETCH e.allotedVehicle fv
			LEFT JOIN FETCH fv.masterVehicle
			WHERE b.orgId = :orgId
			  AND e.driverId = :driverId
			  AND e.dutyId = :dutyId
		""")
	Optional<BookingEntry> findForDriverSelf(
		@Param("orgId") String orgId,
		@Param("driverId") String driverId,
		@Param("dutyId") String dutyId
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

		/*
		 * PESSIMISTIC_WRITE on the primary key only, deliberately WITHOUT a
		 * JOIN FETCH to booking -- unlike lockByDutyIdAndOrgId above, whose
		 * JOIN FETCH e.booking makes MariaDB's FOR UPDATE (no "OF" clause
		 * support) lock the joined booking row too, for the entire duration
		 * of the caller's transaction. That held lock is what caused the
		 * driver duty-end payment QR lock-wait-timeout: any later
		 * REQUIRES_NEW step touching that same booking row hangs until the
		 * outer transaction commits, which can't happen until the
		 * REQUIRES_NEW call returns. Callers should authorize (org check)
		 * via the unlocked findByDutyIdAndOrgId query first, then take this
		 * lock by id -- see ExternalDriverDutyService.submitStart/submitEnd.
		 */
		@Lock(LockModeType.PESSIMISTIC_WRITE)
		@Query("SELECT e FROM BookingEntry e WHERE e.id = :id")
		Optional<BookingEntry> lockById(@Param("id") String id);

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

	/* -------------------------------------------------
	   P4 analytics aggregates -- real GROUP BY queries over
	   completed duties, no fabricated/estimated data.
	   ------------------------------------------------- */

	@Query("""
		SELECT e.fleetVehicleId, COUNT(e), SUM(e.closingKM - e.startingKM)
		FROM BookingEntry e
		WHERE e.booking.orgId = :orgId
		  AND e.status = com.core.models.enums.DutyStatus.COMPLETED
		  AND e.fleetVehicleId IS NOT NULL
		  AND e.closingKM IS NOT NULL
		  AND e.startingKM IS NOT NULL
		  AND (:from IS NULL OR e.startAt >= :from)
		  AND (:to IS NULL OR e.startAt <= :to)
		GROUP BY e.fleetVehicleId
	""")
	List<Object[]> aggregateVehicleUtilization(
			@Param("orgId") String orgId,
			@Param("from") Instant from,
			@Param("to") Instant to
	);

	@Query("""
		SELECT e.driverId, COUNT(e)
		FROM BookingEntry e
		WHERE e.booking.orgId = :orgId
		  AND e.status = com.core.models.enums.DutyStatus.COMPLETED
		  AND e.driverId IS NOT NULL
		  AND (:from IS NULL OR e.startAt >= :from)
		  AND (:to IS NULL OR e.startAt <= :to)
		GROUP BY e.driverId
	""")
	List<Object[]> aggregateDriverCompletedDuties(
			@Param("orgId") String orgId,
			@Param("from") Instant from,
			@Param("to") Instant to
	);

	@Query("""
		SELECT e
		FROM BookingEntry e
		JOIN FETCH e.booking b
		WHERE b.orgId = :orgId
		  AND e.status = com.core.models.enums.DutyStatus.REQUESTED
		ORDER BY e.reportingTime ASC
	""")
	List<BookingEntry> findRequestedDutiesForDispatch(@Param("orgId") String orgId);

	Optional<BookingEntry> findFirstByFleetVehicleIdAndStatusOrderByEndAtDesc(
			String fleetVehicleId, DutyStatus status);

	@Query("SELECT COUNT(e) FROM BookingEntry e WHERE e.booking.orgId = :orgId AND e.status IN :statuses")
	long countByOrgIdAndStatusIn(@Param("orgId") String orgId, @Param("statuses") List<DutyStatus> statuses);

	@Query("""
		SELECT COUNT(DISTINCT e.driverId)
		FROM BookingEntry e
		WHERE e.booking.orgId = :orgId
		  AND e.status IN :statuses
		  AND e.driverId IS NOT NULL
	""")
	long countDistinctBusyDrivers(@Param("orgId") String orgId, @Param("statuses") List<DutyStatus> statuses);

	@Query("""
		SELECT MAX(e.closingKM)
		FROM BookingEntry e
		WHERE e.booking.orgId = :orgId
		  AND e.fleetVehicleId = :fleetVehicleId
		  AND e.closingKM IS NOT NULL
	""")
	Integer findMaxClosingKmForVehicle(@Param("orgId") String orgId, @Param("fleetVehicleId") String fleetVehicleId);

	/*
	 * P1.4 -- batch equivalent of findMaxClosingKmForVehicle, used by
	 * VehicleMaintenanceService.predict, which previously called the
	 * single-vehicle version once per vehicle (1+N). A fleetVehicleId with no
	 * matching rows is simply absent from the result -- callers must treat
	 * that the same as the single-vehicle method's null return.
	 * Row shape: [0]=fleetVehicleId (String), [1]=maxClosingKm (Integer).
	 */
	@Query("""
		SELECT e.fleetVehicleId, MAX(e.closingKM)
		FROM BookingEntry e
		WHERE e.booking.orgId = :orgId
		  AND e.fleetVehicleId IN :fleetVehicleIds
		  AND e.closingKM IS NOT NULL
		GROUP BY e.fleetVehicleId
	""")
	List<Object[]> findMaxClosingKmForVehicles(
			@Param("orgId") String orgId, @Param("fleetVehicleIds") java.util.Collection<String> fleetVehicleIds);

	/*
	 * Real historical completed-trip data for FareRecommendationService --
	 * distance-proximity filtering happens in Java (see that service) since
	 * it needs closingKM-startingKM, which JPQL can compute but bucketing
	 * "within +/-25% of a target" is clearer expressed in Java over a
	 * moderately-sized real result set than as a parameterized JPQL range.
	 */
	@Query("""
		SELECT e
		FROM BookingEntry e
		WHERE e.booking.orgId = :orgId
		  AND e.status = com.core.models.enums.DutyStatus.COMPLETED
		  AND e.masterVehicleId = :masterVehicleId
		  AND e.pack.dutyType = :dutyType
		  AND e.startingKM IS NOT NULL
		  AND e.closingKM IS NOT NULL
		  AND e.dutyTotal IS NOT NULL
	""")
	List<BookingEntry> findCompletedForFareRecommendation(
			@Param("orgId") String orgId,
			@Param("masterVehicleId") String masterVehicleId,
			@Param("dutyType") com.core.models.enums.DutyType dutyType
	);
}

package com.core.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.DriverDutyCheckpoint;
import com.core.models.enums.DriverDutyCheckpointType;

@Repository
public interface DriverDutyCheckpointRepository extends JpaRepository<DriverDutyCheckpoint, String> {

	Optional<DriverDutyCheckpoint> findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(
		String bookingEntryId,
		DriverDutyCheckpointType checkpointType
	);

	boolean existsByBookingEntry_IdAndCheckpointType(
		String bookingEntryId,
		DriverDutyCheckpointType checkpointType
	);

	/*
	 * Real proxy for "where is this idle driver right now" -- there is no
	 * live-tracking concept for a driver who isn't on an active duty
	 * (DriverDutyLiveLocation is keyed by dutyId and only populated while
	 * RUNNING). The driver's most recent END checkpoint is their last known,
	 * real, GPS-captured position -- used by DispatchSuggestionService.
	 */
	Optional<DriverDutyCheckpoint> findFirstByDriverIdAndCheckpointTypeOrderBySubmittedAtDesc(
		String driverId,
		DriverDutyCheckpointType checkpointType
	);

	/*
	 * P1.5 -- batch equivalent of findFirstByDriverIdAndCheckpointTypeOrderBySubmittedAtDesc,
	 * used by DispatchSuggestionService to get every candidate driver's last
	 * known position in one query instead of once per driver. A correlated
	 * MAX(submittedAt) subquery keeps only the latest row per driver -- not a
	 * full history load, since END checkpoints accumulate one per completed
	 * duty and can be large per driver over a career. Same scope as the
	 * single-driver method (no orgId filter -- driverId alone is already
	 * org-scoped by every caller) and the same non-deterministic tie-break if
	 * two checkpoints for one driver somehow share the exact same submittedAt.
	 */
	@Query("""
		SELECT c
		FROM DriverDutyCheckpoint c
		WHERE c.driverId IN :driverIds
		  AND c.checkpointType = :checkpointType
		  AND c.submittedAt = (
		      SELECT MAX(c2.submittedAt)
		      FROM DriverDutyCheckpoint c2
		      WHERE c2.driverId = c.driverId
		        AND c2.checkpointType = c.checkpointType
		  )
	""")
	List<DriverDutyCheckpoint> findLatestByDriverIdsAndCheckpointType(
			@Param("driverIds") Collection<String> driverIds,
			@Param("checkpointType") DriverDutyCheckpointType checkpointType);
}
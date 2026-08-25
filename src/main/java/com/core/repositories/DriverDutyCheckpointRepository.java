package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
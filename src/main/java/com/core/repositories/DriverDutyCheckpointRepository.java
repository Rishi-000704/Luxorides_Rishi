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
}
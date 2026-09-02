package com.core.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DriverDutySosAlert;

@Repository
public interface DriverDutySosAlertRepository extends JpaRepository<DriverDutySosAlert, String> {

	Optional<DriverDutySosAlert> findFirstByDutyIdAndCreatedAtAfterOrderByCreatedAtDesc(String dutyId, Instant after);
}

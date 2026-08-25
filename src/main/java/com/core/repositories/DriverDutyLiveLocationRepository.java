package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DriverDutyLiveLocation;

@Repository
public interface DriverDutyLiveLocationRepository extends JpaRepository<DriverDutyLiveLocation, String> {

	Optional<DriverDutyLiveLocation> findByDutyId(String dutyId);

	Optional<DriverDutyLiveLocation> findByDutyIdAndOrgId(String dutyId, String orgId);
}

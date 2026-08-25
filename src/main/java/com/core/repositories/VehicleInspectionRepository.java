package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.VehicleInspection;

@Repository
public interface VehicleInspectionRepository extends JpaRepository<VehicleInspection, String> {

	Optional<VehicleInspection> findByDutyIdAndOrgId(String dutyId, String orgId);
}

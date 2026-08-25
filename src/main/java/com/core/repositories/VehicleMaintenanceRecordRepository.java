package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.VehicleMaintenanceRecord;

@Repository
public interface VehicleMaintenanceRecordRepository extends JpaRepository<VehicleMaintenanceRecord, String> {

	List<VehicleMaintenanceRecord> findByFleetVehicleIdAndOrgIdOrderByServiceDateDesc(String fleetVehicleId, String orgId);

	Optional<VehicleMaintenanceRecord> findFirstByFleetVehicleIdAndOrgIdOrderByServiceDateDesc(String fleetVehicleId, String orgId);

	List<VehicleMaintenanceRecord> findByOrgIdOrderByServiceDateDesc(String orgId);
}

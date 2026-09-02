package com.core.repositories;

import java.util.Collection;
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

	/*
	 * P1.4 -- batch source for VehicleMaintenanceService.predict, which
	 * previously called findFirstByFleetVehicleIdAndOrgIdOrderByServiceDateDesc
	 * once per vehicle (1+N). Records per vehicle are a handful over its
	 * lifetime (real admin-entered service history, not a high-volume
	 * table), so fetching every matching row in one query and picking the
	 * latest per vehicle in Java is simpler and just as correct as a
	 * per-vehicle "latest row" SQL construct -- same "least complex correct
	 * fix" as the other batches in this checkpoint.
	 */
	List<VehicleMaintenanceRecord> findByOrgIdAndFleetVehicleIdIn(String orgId, Collection<String> fleetVehicleIds);
}

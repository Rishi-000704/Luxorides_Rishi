package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.FleetVehicle;

@Repository
public interface FleetVehicleRepository extends JpaRepository<FleetVehicle, String> {
	
	Optional<FleetVehicle> findByRegistrationNumber(String registrationNumber);

	Optional<FleetVehicle> findByIdAndOrgId(String id, String orgId);

	List<FleetVehicle> findByOrgId(String orgId);
	
	public List<FleetVehicle> findByMasterVehicleIdAndOrgId(String masterVehicleId, String orgId);

	public List<FleetVehicle> findByClientIdAndOrgId(String clientId, String orgId);

	public Page<FleetVehicle> findByOrgId(String orgId, Pageable pageable);

	@Query("SELECT e FROM FleetVehicle e WHERE e.orgId = :orgId AND "
			+ "(LOWER(e.garageLocation) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.registrationNumber) LIKE LOWER(CONCAT('%', :searchStr, '%')))")
	public Page<FleetVehicle> findByOrgIdAndSearch(@Param("orgId") String orgId, @Param("searchStr") String searchStr,
			Pageable pageable);

	@Query("""
			    SELECT fv
			    FROM FleetVehicle fv
			    LEFT JOIN FETCH fv.client
			    LEFT JOIN FETCH fv.masterVehicle
			    WHERE fv.orgId = :orgId
			      AND (
			        :searchStr IS NULL
			        OR LOWER(fv.registrationNumber) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			        OR LOWER(fv.garageLocation.formattedAddress) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			        OR LOWER(fv.client.phone) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			      )
			""")
	Page<FleetVehicle> findPageWithClient(@Param("orgId") String orgId, @Param("searchStr") String searchStr,
			Pageable pageable);

}

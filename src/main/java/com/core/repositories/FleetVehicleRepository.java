package com.core.repositories;

import java.util.Collection;
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

	/*
	 * P1.4 -- batch lookup for FleetAnalyticsService.vehicleUtilization, which
	 * previously called findByIdAndOrgId once per aggregate row (1+N). JOIN
	 * FETCH masterVehicle too, since every caller of this method immediately
	 * reads vehicle.getMasterVehicle().getName() -- without it, batching the
	 * FleetVehicle lookup alone would just move the N+1 one hop deeper (one
	 * lazy-load per distinct master vehicle instead of per row).
	 */
	@Query("""
		SELECT fv
		FROM FleetVehicle fv
		LEFT JOIN FETCH fv.masterVehicle
		WHERE fv.orgId = :orgId
		  AND fv.id IN :ids
	""")
	List<FleetVehicle> findByOrgIdAndIdIn(@Param("orgId") String orgId, @Param("ids") Collection<String> ids);

	/*
	 * P1.4 -- used only by VehicleMaintenanceService.predict, which iterates
	 * every vehicle and reads vehicle.getMasterVehicle().getName() for each.
	 * A dedicated method (not a change to the widely-shared findByOrgId
	 * above, which DispatchSuggestionService and FleetVehicleDataExchangeHandler
	 * also call and don't need this join for) so this fix doesn't alter
	 * those other callers' query shape.
	 */
	@Query("""
		SELECT fv
		FROM FleetVehicle fv
		LEFT JOIN FETCH fv.masterVehicle
		WHERE fv.orgId = :orgId
	""")
	List<FleetVehicle> findByOrgIdFetchMasterVehicle(@Param("orgId") String orgId);
	
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

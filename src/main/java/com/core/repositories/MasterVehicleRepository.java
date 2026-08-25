package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.core.models.MasterVehicle;
import com.core.models.enums.DutyType;
import com.core.models.enums.VehicleStatus;

public interface MasterVehicleRepository extends JpaRepository<MasterVehicle, String> {

	Optional<MasterVehicle> findByIdAndOrgId(String id, String orgId);

	Optional<MasterVehicle> findByOrgIdAndSlug(String orgId, String slug);

	List<MasterVehicle> findByOrgId(String orgId);

	Page<MasterVehicle> findByOrgId(String orgId, Pageable pageable);

	@Query("SELECT e FROM MasterVehicle e WHERE e.orgId = :orgId AND "
			+ "(LOWER(e.name) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.fuelSystem) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.fuelConsumption) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.vehicleColor) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.brand) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.seats) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.doors) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.transmissionType) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.horsePower) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.vehicleClass) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.modelYear) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.performance) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.slug) LIKE LOWER(CONCAT('%', :searchStr, '%')) "
			+ "OR LOWER(e.remarks) LIKE LOWER(CONCAT('%', :searchStr, '%')))")
	Page<MasterVehicle> findByOrgIdAndSearch(@Param("orgId") String orgId, @Param("searchStr") String searchStr,
			Pageable pageable);

	/*
	 * ===================================================== TRENDING VEHICLES
	 * =====================================================
	 */

	@Query("""
			    SELECT DISTINCT v
			    FROM Package p
			    JOIN p.masterVehicle v
			    WHERE p.orgId = :orgId
			      AND p.forSales = true
			      AND p.location = :location
			      AND v.status = :status
			    ORDER BY v.popularity DESC, v.rating DESC
			""")
	Page<MasterVehicle> findTrendingByLocation(@Param("orgId") String orgId, @Param("location") String location,
			@Param("status") VehicleStatus status, Pageable pageable);

	/*
	 * ===================================================== EXPLORER – FILTERED +
	 * SEARCHED =====================================================
	 */
	@Query("""
			    SELECT DISTINCT v
			    FROM Package p
			    JOIN p.masterVehicle v
			    WHERE p.orgId = :orgId
			      AND p.forSales = true
			     AND LOWER(TRIM(p.location)) = LOWER(TRIM(:location))
			      AND v.status = :status
			      AND (:searchStr IS NULL OR (
			            LOWER(v.name) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			         OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			         OR LOWER(v.category) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			      ))
			      AND (:brands IS NULL OR v.brand IN :brands)
			      AND (:categories IS NULL OR v.category IN :categories)
			""")
	Page<MasterVehicle> findExplorerByLocation(@Param("orgId") String orgId, @Param("location") String location,
			@Param("status") VehicleStatus status, @Param("searchStr") String searchStr,
			@Param("brands") List<String> brands, @Param("categories") List<String> categories, Pageable pageable);

	/*
	 * ===================================================== FILTER METADATA
	 * =====================================================
	 */

	@Query("""
			    SELECT DISTINCT v.brand
			    FROM MasterVehicle v
			    WHERE v.orgId = :orgId
			      AND v.status = :status
			      AND v.brand IS NOT NULL
			    ORDER BY v.brand
			""")
	List<String> findDistinctBrands(@Param("orgId") String orgId, @Param("status") VehicleStatus status);

	@Query("""
			    SELECT DISTINCT v.category
			    FROM MasterVehicle v
			    WHERE v.orgId = :orgId
			      AND v.status = :status
			      AND v.category IS NOT NULL
			    ORDER BY v.category
			""")
	List<String> findDistinctCategories(@Param("orgId") String orgId, @Param("status") VehicleStatus status);
	

	@Query("""
		    SELECT DISTINCT v
		    FROM Package p
		    JOIN p.masterVehicle v
		    WHERE p.orgId = :orgId
		      AND p.forSales = true
		      AND LOWER(TRIM(p.location)) = LOWER(TRIM(:location))
		      AND p.dutyType IN :dutyTypes
		      AND v.status = :status
		      AND (
		            p.clientId IS NULL
		            OR p.clientId = :clientId
		          )
		""")
		Page<MasterVehicle> findVehicleCatalogByLocationAndDuty(
		        @Param("orgId") String orgId,
		        @Param("clientId") String clientId,
		        @Param("location") String location,
		        @Param("dutyTypes") List<DutyType> dutyTypes,
		        @Param("status") VehicleStatus status,
		        Pageable pageable
		);


}

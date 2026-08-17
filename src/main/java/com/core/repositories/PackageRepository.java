package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.core.models.Package;
import com.core.models.enums.DutyType;

public interface PackageRepository extends JpaRepository<Package, String> {

	Optional<Package> findByIdAndOrgId(String id, String orgId);

	List<Package> findByOrgId(String orgId);

	@Query("""
			    SELECT p
			    FROM Package p
			    JOIN p.masterVehicle v
			    WHERE p.orgId = :orgId
			      AND v.id = :vehicleId
			      AND p.forSales = true
			      AND LOWER(p.location) = LOWER(:location)
			      AND (
			            p.clientId IS NULL
			            OR p.clientId = :clientId
			          )
			""")
	List<Package> findForSalesByVehicleAndLocation(@Param("orgId") String orgId, @Param("vehicleId") String vehicleId,
			@Param("clientId") String clientId, @Param("location") String location);

	/*
	 * ===================================================== ADMIN LISTING
	 * =====================================================
	 */

	@Query("""
				SELECT p
				FROM Package p
				WHERE p.orgId = :orgId
				  AND p.masterVehicleId = :masterVehicleId
				  AND p.forSales = :forSales
				  AND (
				       p.clientId IS NULL
				    OR p.clientId = :clientId
				  )
				ORDER BY
				  p.clientId NULLS FIRST,
				  p.dutyType ASC
			""")
	List<Package> getPackageListByMasterVehicleForSales(@Param("orgId") String orgId,
			@Param("masterVehicleId") String masterVehicleId, @Param("clientId") String clientId,
			@Param("forSales") Boolean forSales);

	/*
	 * ===================================================== ADMIN LISTING
	 * =====================================================
	 */

	Page<Package> findByOrgIdAndForSales(String orgId, boolean forSales, Pageable pageable);

	@Query("""
				SELECT p
				FROM Package p
				WHERE p.orgId = :orgId
				  AND p.forSales = :forSales
				  AND (
				       LOWER(p.dutyType) LIKE LOWER(CONCAT('%', :search, '%'))
				    OR LOWER(p.location) LIKE LOWER(CONCAT('%', :search, '%'))
				  )
			""")
	Page<Package> searchForAdmin(@Param("orgId") String orgId, @Param("forSales") boolean forSales,
			@Param("search") String search, Pageable pageable);

	/*
	 * ===================================================== GENERIC PAGE (SAFE
	 * PAGINATION) =====================================================
	 */

	@EntityGraph(attributePaths = { "client", "masterVehicle" })
	@Query("""
				SELECT p
				FROM Package p
				LEFT JOIN p.client c
				LEFT JOIN p.masterVehicle v
				WHERE p.orgId = :orgId
				  AND (
				       :searchStr IS NULL

				    OR LOWER(p.dutyType) LIKE LOWER(CONCAT('%', :searchStr, '%'))
				    OR LOWER(p.location) LIKE LOWER(CONCAT('%', :searchStr, '%'))

				    OR LOWER(p.scope) LIKE LOWER(CONCAT('%', :searchStr, '%'))

				    OR (
				         c IS NOT NULL
				     AND (
				           LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :searchStr, '%'))
				        OR LOWER(c.name.lastName)  LIKE LOWER(CONCAT('%', :searchStr, '%'))
				        OR LOWER(CONCAT(c.name.firstName, ' ', c.name.lastName))
				           LIKE LOWER(CONCAT('%', :searchStr, '%'))
				     )
				    )

				    OR (
				         v IS NOT NULL
				     AND LOWER(v.name) LIKE LOWER(CONCAT('%', :searchStr, '%'))
				    )
				  )
			""")
	Page<Package> getPage(@Param("orgId") String orgId, @Param("searchStr") String searchStr, Pageable pageable);

	/*
	 * ===================================================== SALES HELPERS
	 * =====================================================
	 */

	@Query("""
				SELECT p
				FROM Package p
				WHERE p.orgId = :orgId
				  AND p.forSales = true
				  AND p.location = :location
			""")
	List<Package> findForSalesByLocation(@Param("orgId") String orgId, @Param("location") String location);
	
	@Query("""
		    SELECT p
		    FROM Package p
		    WHERE p.orgId = :orgId
		      AND p.masterVehicleId = :masterVehicleId
		      AND p.dutyType IN :dutyTypes
		      AND p.forSales = true
		      AND LOWER(TRIM(p.location)) = LOWER(TRIM(:location))
		      AND (p.clientId = :clientId OR p.clientId IS NULL)
		    ORDER BY p.baseFare.amount ASC
		""")
		List<Package> findEligiblePackages(
		    @Param("orgId") String orgId,
		    @Param("clientId") String clientId,
		    @Param("masterVehicleId") String masterVehicleId,
		    @Param("dutyTypes") List<DutyType> dutyTypes,
		    @Param("location") String location
		);

	@Query("""
        SELECT p
        FROM Package p
        LEFT JOIN FETCH p.client c
        LEFT JOIN FETCH p.masterVehicle mv
        WHERE p.orgId = :orgId
          AND p.masterVehicleId = :masterVehicleId
          AND (:forSales IS NULL OR p.forSales = :forSales)
          AND (
                :clientId IS NULL
             OR :clientId = ''
             OR p.clientId = :clientId
          )
        ORDER BY
          p.location ASC,
          p.scope ASC,
          p.dutyType ASC,
          p.clientId ASC
        """)
	List<Package> findByMasterVehicleForAdmin(
			@Param("orgId") String orgId,
			@Param("masterVehicleId") String masterVehicleId,
			@Param("forSales") Boolean forSales,
			@Param("clientId") String clientId
	);

	/* =====================================================
	 * PURCHASE HELPERS
	 * =====================================================
	 */

	@Query("""
	SELECT p
	FROM Package p
	LEFT JOIN FETCH p.masterVehicle
	LEFT JOIN FETCH p.client
	WHERE p.orgId = :orgId
	  AND p.forSales = false
	  AND p.masterVehicleId = :masterVehicleId
	  AND p.dutyType = :dutyType
	  AND (p.clientId IS NULL OR p.clientId = :vendorId)
	ORDER BY
	  CASE WHEN p.clientId = :vendorId THEN 0 ELSE 1 END,
	  p.baseFare.amount ASC
""")
	List<Package> findPurchasePackageOptions(
			@Param("orgId") String orgId,
			@Param("vendorId") String vendorId,
			@Param("masterVehicleId") String masterVehicleId,
			@Param("dutyType") DutyType dutyType
	);

	@Query("""
		SELECT p
		FROM Package p
		LEFT JOIN FETCH p.client c
		LEFT JOIN FETCH p.masterVehicle v
		WHERE p.orgId = :orgId
		  AND p.clientId = :clientId
		  AND (:forSales IS NULL OR p.forSales = :forSales)
		ORDER BY
		  p.location ASC,
		  v.name ASC,
		  p.dutyType ASC,
		  p.baseFare.amount ASC
		""")
	List<Package> findByClientForProfile(
			@Param("orgId") String orgId,
			@Param("clientId") String clientId,
			@Param("forSales") Boolean forSales
	);

}

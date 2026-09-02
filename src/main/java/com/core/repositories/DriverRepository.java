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

import com.core.models.Driver;
@Repository
public interface DriverRepository extends JpaRepository<Driver, String> {

	Optional<Driver> findByIdAndOrgId(String id, String orgId);

	Driver findByPhoneAndOrgId(String phone, String orgId);

	Optional<Driver> findByUserId(String userId);

	List<Driver> findByOrgId(String orgId);

	long countByOrgId(String orgId);

	List<Driver> findByClientIdAndOrgId(String clientId, String orgId);

	/*
	 * P1.4 -- batch lookup for FleetAnalyticsService.driverAnalytics, which
	 * previously called findByIdAndOrgId once per aggregate row (1+N).
	 */
	List<Driver> findByOrgIdAndIdIn(String orgId, Collection<String> ids);

	@Query("""
			    SELECT x
			    FROM Driver x
			    LEFT JOIN FETCH x.client
			    WHERE x.orgId = :orgId
			      AND (
			        :searchStr IS NULL
			        OR LOWER(x.phone) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			        OR LOWER(x.licenseNumber) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			        OR LOWER(x.address.formattedAddress) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			     	OR LOWER(x.address.city) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			     	OR LOWER(x.address.state) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			     	OR LOWER(x.name.firstName) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			     	OR LOWER(x.name.lastName) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			     	OR LOWER(x.name.salutation) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			      )
			""")
	Page<Driver> getPage(@Param("orgId") String orgId, @Param("searchStr") String searchStr, Pageable pageable);
}

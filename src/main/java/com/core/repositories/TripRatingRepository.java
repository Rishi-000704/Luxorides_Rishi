package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.TripRating;

@Repository
public interface TripRatingRepository extends JpaRepository<TripRating, String> {

	Optional<TripRating> findByDutyIdAndOrgId(String dutyId, String orgId);

	boolean existsByDutyIdAndOrgId(String dutyId, String orgId);

	@Query("select avg(r.stars) from TripRating r where r.driverId = :driverId and r.orgId = :orgId")
	Double findAverageStarsByDriverIdAndOrgId(@Param("driverId") String driverId, @Param("orgId") String orgId);

	long countByDriverIdAndOrgId(String driverId, String orgId);
}

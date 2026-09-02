package com.core.repositories;

import java.util.Collection;
import java.util.List;
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

	/*
	 * P1.4 -- batch equivalent of findAverageStarsByDriverIdAndOrgId +
	 * countByDriverIdAndOrgId together, used by FleetAnalyticsService.driverAnalytics
	 * and ClientBookingAssembler.mapEntry, both of which previously called
	 * those two methods once per driver/entry (1+2N). GROUP BY naturally
	 * omits a driverId with zero ratings -- callers must treat a missing row
	 * the same as the single-driver methods' (null average, 0 count).
	 * Row shape: [0]=driverId (String), [1]=avgStars (Double), [2]=count (Long).
	 */
	@Query("""
		SELECT r.driverId, AVG(r.stars), COUNT(r)
		FROM TripRating r
		WHERE r.orgId = :orgId AND r.driverId IN :driverIds
		GROUP BY r.driverId
	""")
	List<Object[]> aggregateStarsByDriverIds(@Param("orgId") String orgId, @Param("driverIds") Collection<String> driverIds);
}

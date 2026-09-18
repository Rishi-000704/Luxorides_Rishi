package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DriverOpsRating;

@Repository
public interface DriverOpsRatingRepository extends JpaRepository<DriverOpsRating, String> {

	Optional<DriverOpsRating> findByOrgIdAndDriverId(String orgId, String driverId);
}

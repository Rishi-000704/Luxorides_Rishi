package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.CityGarage;

@Repository
public interface CityGarageRepository extends JpaRepository<CityGarage, String> {
	Optional<CityGarage> findByOrgIdAndCity(String orgId, String city);

	List<CityGarage> findByOrgId(String orgId);
}

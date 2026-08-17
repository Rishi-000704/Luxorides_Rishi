package com.core.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.core.models.Estimate;
import com.core.models.enums.EstimateStatus;

import jakarta.persistence.LockModeType;

public interface EstimateRepository extends JpaRepository<Estimate, String> {

	Optional<Estimate> findByEstimateIdAndOrgId(String estimateId, String orgId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Estimate e where e.id = :id and e.orgId = :orgId")
	Optional<Estimate> lockByIdAndOrgId(@Param("id") String id, @Param("orgId") String orgId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Estimate e where e.estimateId = :estimateId and e.orgId = :orgId")
	Optional<Estimate> lockByEstimateIdAndOrgId(@Param("estimateId") String estimateId, @Param("orgId") String orgId);

	@Query("""
			SELECT DISTINCT e
			FROM Estimate e
			LEFT JOIN e.client c
			WHERE e.orgId = :orgId
			  AND (:status IS NULL OR e.status = :status)
			  AND (
			       :search IS NULL OR :search = ''
			       OR LOWER(e.estimateId) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(e.remarks) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(c.name.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(c.name.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(CONCAT(c.name.firstName, ' ', c.name.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))
			  )
			""")
	Page<Estimate> search(
			@Param("orgId") String orgId,
			@Param("status") EstimateStatus status,
			@Param("search") String search,
			Pageable pageable);
}
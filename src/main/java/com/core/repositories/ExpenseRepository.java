package com.core.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.Expense;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, String> {

	List<Expense> findByOrgIdOrderByIncurredAtDesc(String orgId);

	Optional<Expense> findByIdAndOrgId(String id, String orgId);

	@Query("""
		SELECT COALESCE(SUM(e.amount), 0)
		FROM Expense e
		WHERE e.orgId = :orgId
		  AND (:from IS NULL OR e.incurredAt >= :from)
		  AND (:to IS NULL OR e.incurredAt <= :to)
	""")
	java.math.BigDecimal sumByOrgIdAndDateRange(
			@Param("orgId") String orgId,
			@Param("from") Instant from,
			@Param("to") Instant to
	);
}

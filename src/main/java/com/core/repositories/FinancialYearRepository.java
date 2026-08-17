package com.core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.FinancialYear;

import jakarta.persistence.LockModeType;

@Repository
public interface FinancialYearRepository extends JpaRepository<FinancialYear, String> {

	List<FinancialYear> findByOrgId(String orgId);

	@Query("""
			SELECT fy
			FROM FinancialYear fy
			WHERE fy.orgId = :orgId
			  AND fy.orgBillingEntityId = :orgBillingEntityId
			  AND CURRENT_DATE BETWEEN fy.startDate AND fy.endDate
			""")
	FinancialYear findCurrentFinancialYear(
			@Param("orgId") String orgId,
			@Param("orgBillingEntityId") String orgBillingEntityId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT fy
			FROM FinancialYear fy
			WHERE fy.orgId = :orgId
			  AND fy.orgBillingEntityId = :orgBillingEntityId
			  AND CURRENT_DATE BETWEEN fy.startDate AND fy.endDate
			""")
	FinancialYear lockCurrentFinancialYear(
			@Param("orgId") String orgId,
			@Param("orgBillingEntityId") String orgBillingEntityId
	);
}
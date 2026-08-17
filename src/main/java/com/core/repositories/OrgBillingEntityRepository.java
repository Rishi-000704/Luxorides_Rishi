package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.core.models.OrgBillingEntity;

public interface OrgBillingEntityRepository extends JpaRepository<OrgBillingEntity, String> {
	Optional<OrgBillingEntity> findByGstRateAndOrgId(Integer gstRate, String orgId);
	
	Optional<OrgBillingEntity> findByIdAndOrgId(String id, String orgId);

	List<OrgBillingEntity> findByOrgId(String orgId);
}

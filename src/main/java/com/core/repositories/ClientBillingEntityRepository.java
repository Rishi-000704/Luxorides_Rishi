package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.core.models.ClientBillingEntity;

public interface ClientBillingEntityRepository extends JpaRepository<ClientBillingEntity, String> {

	Optional<ClientBillingEntity> findByIdAndOrgId(String id, String orgId);

	Optional<ClientBillingEntity> findByGstinAndOrgId(String gstin, String orgId);

	List<ClientBillingEntity> findByIdIn(List<String> ids);

}

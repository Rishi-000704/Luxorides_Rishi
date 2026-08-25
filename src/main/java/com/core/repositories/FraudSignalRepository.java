package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.FraudSignal;
import com.core.models.enums.FraudSignalStatus;
import com.core.models.enums.FraudSignalType;

@Repository
public interface FraudSignalRepository extends JpaRepository<FraudSignal, String> {

	List<FraudSignal> findByOrgIdOrderByCreatedAtDesc(String orgId);

	Optional<FraudSignal> findByIdAndOrgId(String id, String orgId);

	boolean existsByOrgIdAndTypeAndClientIdAndStatus(
			String orgId, FraudSignalType type, String clientId, FraudSignalStatus status);

	boolean existsByOrgIdAndTypeAndDriverIdAndDutyIdAndStatus(
			String orgId, FraudSignalType type, String driverId, String dutyId, FraudSignalStatus status);
}

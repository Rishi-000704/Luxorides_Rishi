package com.core.repositories;

import java.util.List;
import java.util.Optional;

import com.core.models.SmsProviderConfig;
import com.core.models.enums.SmsProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsProviderConfigRepository extends JpaRepository<SmsProviderConfig, String> {

    Optional<SmsProviderConfig> findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc(String orgId);

    Optional<SmsProviderConfig> findFirstByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<SmsProviderConfig> findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc(String orgId);

    Optional<SmsProviderConfig> findFirstByOrgIdAndActiveTrueOrderByUpdatedAtDesc(String orgId);

    Optional<SmsProviderConfig> findByOrgIdAndProviderType(String orgId, SmsProviderType providerType);

    List<SmsProviderConfig> findByOrgIdAndIdNot(String orgId, String id);
}
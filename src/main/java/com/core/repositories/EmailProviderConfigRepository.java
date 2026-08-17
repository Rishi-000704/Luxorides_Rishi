package com.core.repositories;

import java.util.List;
import java.util.Optional;

import com.core.models.EmailProviderConfig;
import com.core.models.enums.EmailProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailProviderConfigRepository extends JpaRepository<EmailProviderConfig, String> {

    Optional<EmailProviderConfig> findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc(String orgId);

    Optional<EmailProviderConfig> findFirstByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<EmailProviderConfig> findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc(String orgId);

    Optional<EmailProviderConfig> findFirstByOrgIdAndActiveTrueOrderByUpdatedAtDesc(String orgId);

    Optional<EmailProviderConfig> findByOrgIdAndProviderType(String orgId, EmailProviderType providerType);

    List<EmailProviderConfig> findByOrgIdAndIdNot(String orgId, String id);
}
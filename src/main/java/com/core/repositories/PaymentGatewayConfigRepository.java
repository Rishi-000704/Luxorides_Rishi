package com.core.repositories;

import java.util.List;
import java.util.Optional;

import com.core.models.PaymentGatewayConfig;
import com.core.models.enums.PaymentGateway;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentGatewayConfigRepository extends JpaRepository<PaymentGatewayConfig, String> {

    Optional<PaymentGatewayConfig> findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc(String orgId);

    Optional<PaymentGatewayConfig> findFirstByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<PaymentGatewayConfig> findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc(String orgId);

    Optional<PaymentGatewayConfig> findFirstByOrgIdAndActiveTrueOrderByUpdatedAtDesc(String orgId);

    Optional<PaymentGatewayConfig> findByOrgIdAndGateway(String orgId, PaymentGateway gateway);

    List<PaymentGatewayConfig> findByOrgIdAndIdNot(String orgId, String id);
}
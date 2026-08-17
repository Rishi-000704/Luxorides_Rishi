package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.core.models.EstimateAccessToken;
import com.core.models.enums.EstimateLinkStatus;

public interface EstimateAccessTokenRepository extends JpaRepository<EstimateAccessToken, String> {

	Optional<EstimateAccessToken> findByTokenHash(String tokenHash);

	Optional<EstimateAccessToken> findTopByEstimateIdAndOrgIdAndStatusOrderByCreatedAtDesc(
			String estimateId,
			String orgId,
			EstimateLinkStatus status);
}
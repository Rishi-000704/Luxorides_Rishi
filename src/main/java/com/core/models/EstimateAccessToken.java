package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.EstimateLinkStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "estimate_access_token", indexes = {
		@Index(name = "idx_estimate_token_hash", columnList = "token_hash", unique = true),
		@Index(name = "idx_estimate_token_estimate", columnList = "estimate_id"),
		@Index(name = "idx_estimate_token_org", columnList = "org_id")
})
public class EstimateAccessToken extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;

	@Column(name = "org_id", nullable = false, length = 40)
	private String orgId;

	@Column(name = "estimate_id", nullable = false, length = 40)
	private String estimateId;

	@Column(name = "token_hash", nullable = false, length = 128, unique = true)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstimateLinkStatus status;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant lastViewedAt;
	private Instant paidAt;
	private Instant revokedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "estimate_id", referencedColumnName = "id", insertable = false, updatable = false)
	private Estimate estimate;
}
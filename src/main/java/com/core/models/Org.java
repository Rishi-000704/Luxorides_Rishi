package com.core.models;

import com.core.models.embedded.DisplayAddress;
import com.core.models.enums.OrgStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Org {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false)
	private String id;

	@Column(nullable = false, length = 40, unique = true)
	private String orgId;

	@Column(nullable = false, length = 100)
	private String orgName;

	@Embedded
	private DisplayAddress address;

	@Column(nullable = false, length = 12)
	private String pan;
	@Column(nullable = false, length = 20)
	private String cin;
	@Column(nullable = false, length = 15)
	private String gstin;
	@Column(nullable = false, length = 15)
	private String phone;
	@Column(length = 15)
	private String alternatePhone;
	@Column(nullable = false, length = 50)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrgStatus status;

	@Column(length = 100)
	private String websiteLink;
	@Column(length = 500)
	private String remarks;

	/*
	 * Per-org cancellation policy (configurable rather than hardcoded, per
	 * product decision). Nulls are treated as "no free window / no fee" by
	 * CancellationPolicyService -- an org that never configures this behaves
	 * exactly as if cancellation were always free, matching today's
	 * (unconfigured) behavior.
	 */
	private Integer cancellationFreeWindowHours;

	@Column(precision = 5, scale = 2)
	private java.math.BigDecimal cancellationFeePercent;

	/*
	 * Dynamic-pricing config. Disabled by default (null/false) -- an org that
	 * never configures this sees the multiplier endpoint return 1.0 (no-op),
	 * matching pre-feature behavior. maxMultiplier bounds how aggressive the
	 * real-time demand/supply-based multiplier (DynamicPricingService) is
	 * ever allowed to go, e.g. 1.50 = capped at 50% above base rate.
	 */
	private boolean dynamicPricingEnabled = false;

	@Column(precision = 4, scale = 2)
	private java.math.BigDecimal dynamicPricingMaxMultiplier;
}

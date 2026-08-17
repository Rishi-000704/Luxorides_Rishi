package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.DriverDutyTokenStatus;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "driver_duty_access_token", indexes = {
		@Index(name = "idx_driver_duty_token_hash", columnList = "token_hash", unique = true),
		@Index(name = "idx_driver_duty_token_duty", columnList = "duty_id"),
		@Index(name = "idx_driver_duty_token_entry", columnList = "booking_entry_id") })
public class DriverDutyAccessToken extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String dutyId;

	@Column(length = 40)
	private String driverId;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	private Instant expiresAt;

	private Instant revokedAt;

	private Instant lastUsedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DriverDutyTokenStatus status = DriverDutyTokenStatus.ACTIVE;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_entry_id", nullable = false)
	private BookingEntry bookingEntry;
}
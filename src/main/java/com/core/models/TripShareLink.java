package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * A public, unauthenticated read-only tracking link for one duty. Only the
 * SHA-256 hash of the raw token is stored (same pattern as
 * DriverDutyAccessToken) so a leaked database row can't be replayed as a
 * live link. Revoked/expired links simply stop resolving -- no PII beyond
 * live position and driver/vehicle first-name-level detail is ever exposed
 * through this token (see PublicTripController).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "trip_share_link")
public class TripShareLink extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String bookingId;

	@Column(name = "duty_id", nullable = false, length = 40)
	private String dutyId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean revoked = false;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_entry_id", nullable = false)
	private BookingEntry bookingEntry;
}

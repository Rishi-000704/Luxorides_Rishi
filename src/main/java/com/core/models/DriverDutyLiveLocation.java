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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Latest-known GPS position for an active duty -- one row per dutyId,
 * upserted on every ping, not an append-only history log (no requirement
 * today for route replay, and an unbounded log would grow without limit
 * over a multi-hour duty pinging every ~15s).
 *
 * Plain nullable lat/long columns rather than the AddressSnapshot embeddable
 * used elsewhere (DriverDutyCheckpoint, BookingEntry) -- AddressSnapshot's
 * formattedAddress is non-nullable, which would force either a reverse-geocode
 * call or a fabricated placeholder string on every single ping.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "driver_duty_live_location", uniqueConstraints = @UniqueConstraint(columnNames = "duty_id"))
public class DriverDutyLiveLocation extends AuditableEntity {

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

	@Column(length = 40)
	private String driverId;

	private Double latitude;

	private Double longitude;

	private Double accuracyMeters;

	private Double headingDegrees;

	private Double speedMps;

	private Instant capturedAt;

	private Instant receivedAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_entry_id", nullable = false)
	private BookingEntry bookingEntry;
}

package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.DriverDutySosStatus;

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

/*
 * A raw safety ping -- lat/long inline rather than AddressSnapshot, since
 * there's no reverse-geocode step in the SOS flow and no reason to block on
 * one. No WS broadcast: the only meaningful consumer is a future ops/admin
 * screen (P4, out of scope), so this is persisted for that purpose only.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "driver_duty_sos_alert", indexes = {
		@Index(name = "idx_driver_sos_duty", columnList = "duty_id"),
		@Index(name = "idx_driver_sos_org_status", columnList = "org_id, status") })
public class DriverDutySosAlert extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String bookingId;

	@Column(nullable = false, length = 40)
	private String dutyId;

	@Column(length = 40)
	private String driverId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DriverDutySosStatus status = DriverDutySosStatus.OPEN;

	private Double latitude;

	private Double longitude;

	private Instant capturedAt;

	@Column(length = 500)
	private String notes;

	@Column(length = 80)
	private String ipAddress;

	@Column(length = 500)
	private String userAgent;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_entry_id", nullable = false)
	private BookingEntry bookingEntry;
}

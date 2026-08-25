package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.DriverDutyIncidentCategory;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * Unlike the SOS ping, an incident report is a discrete authored event worth
 * a real address (reverse-geocoded like a checkpoint), not just raw
 * coordinates. No WS broadcast here either -- same as SOS, the only
 * meaningful consumer is a future ops/admin screen (P4, out of scope).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "driver_duty_incident_report", indexes = {
		@Index(name = "idx_driver_incident_duty", columnList = "duty_id"),
		@Index(name = "idx_driver_incident_org", columnList = "org_id") })
public class DriverDutyIncidentReport extends AuditableEntity {

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
	@Column(nullable = false, length = 30)
	private DriverDutyIncidentCategory category;

	@Column(nullable = false, length = 1000)
	private String description;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "formattedAddress", column = @Column(name = "location_address", length = 300)),
			@AttributeOverride(name = "googlePlaceId", column = @Column(name = "location_place_id", length = 100)),
			@AttributeOverride(name = "latitude", column = @Column(name = "location_latitude")),
			@AttributeOverride(name = "longitude", column = @Column(name = "location_longitude")) })
	private AddressSnapshot location;

	@Column(length = 120)
	private String photo1;

	@Column(length = 120)
	private String photo2;

	@Column(length = 120)
	private String photo3;

	private Instant submittedAt;

	@Column(length = 80)
	private String ipAddress;

	@Column(length = 500)
	private String userAgent;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_entry_id", nullable = false)
	private BookingEntry bookingEntry;
}

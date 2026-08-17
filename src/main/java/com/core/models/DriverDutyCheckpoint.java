package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.DriverDutyCheckpointStatus;
import com.core.models.enums.DriverDutyCheckpointType;

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

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "driver_duty_checkpoint", indexes = { @Index(name = "idx_driver_checkpoint_duty", columnList = "duty_id"),
		@Index(name = "idx_driver_checkpoint_entry_type", columnList = "booking_entry_id, checkpoint_type") })
public class DriverDutyCheckpoint extends AuditableEntity {

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

	@Column(length = 40)
	private String fleetVehicleId;

	@Enumerated(EnumType.STRING)
	@Column(name = "checkpoint_type", nullable = false, length = 20)
	private DriverDutyCheckpointType checkpointType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DriverDutyCheckpointStatus status = DriverDutyCheckpointStatus.ACCEPTED;

	private Integer odometerKm;

	@Column(length = 120)
	private String odometerPhoto;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "formattedAddress", column = @Column(name = "location_address", length = 300)),
			@AttributeOverride(name = "googlePlaceId", column = @Column(name = "location_place_id", length = 100)),
			@AttributeOverride(name = "latitude", column = @Column(name = "location_latitude")),
			@AttributeOverride(name = "longitude", column = @Column(name = "location_longitude")) })
	private AddressSnapshot location;

	private Double accuracyMeters;

	private Instant locationCapturedAt;

	private Instant submittedAt;

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
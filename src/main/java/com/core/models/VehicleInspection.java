package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.VehicleConditionRating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * One row per duty (upsert, latest-only) -- the pre-duty structured
 * inspection a driver submits during Duty Readiness. Lives independently of
 * DriverDutyCheckpoint (different shape, different timing: this happens
 * before startDuty mints the execution token, checkpoints happen during
 * execution) rather than overloading that entity's checkpointType semantics.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "vehicle_inspection", uniqueConstraints = @UniqueConstraint(columnNames = "duty_id"))
public class VehicleInspection extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(name = "duty_id", nullable = false, length = 40)
	private String dutyId;

	@Column(length = 40)
	private String driverId;

	@Column(length = 40)
	private String fleetVehicleId;

	@Column(length = 120)
	private String exteriorFrontPhoto;

	@Column(length = 120)
	private String exteriorBackPhoto;

	@Column(length = 120)
	private String exteriorLeftPhoto;

	@Column(length = 120)
	private String exteriorRightPhoto;

	@Column(length = 120)
	private String interiorDashboardPhoto;

	@Column(length = 120)
	private String interiorFrontSeatsPhoto;

	@Column(length = 120)
	private String interiorBackSeatsPhoto;

	@Column(length = 120)
	private String interiorBootSpacePhoto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VehicleConditionRating exteriorCondition = VehicleConditionRating.GOOD;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VehicleConditionRating interiorCondition = VehicleConditionRating.GOOD;

	@Column(length = 1000)
	private String damageNotes;

	private Instant submittedAt;
}

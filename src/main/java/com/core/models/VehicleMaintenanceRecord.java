package com.core.models;

import java.math.BigDecimal;
import java.time.Instant;

import com.core.models.embedded.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Real, admin-entered service-history row -- there was no maintenance
 * tracking anywhere in the codebase before this. MaintenancePredictionService
 * flags vehicles due/overdue purely from real odometer readings (captured at
 * duty start/end today) and real recorded service events -- no fabricated
 * IoT/telemetry data, since none exists.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "vehicle_maintenance_record")
public class VehicleMaintenanceRecord extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String fleetVehicleId;

	@Column(nullable = false, length = 100)
	private String serviceType;

	@Column(nullable = false)
	private Instant serviceDate;

	@Column(nullable = false)
	private Integer odometerKmAtService;

	@Column(precision = 10, scale = 2)
	private BigDecimal cost;

	@Column(length = 500)
	private String remarks;
}

package com.core.models;

import com.core.models.embedded.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Ops's own current assessment of a driver -- separate from TripRating (the
 * customer's per-duty rating). One row per driver, upserted in place (same
 * pattern as VehicleInspection's one-row-per-duty upsert, just scoped to the
 * driver instead): ops updates this whenever their assessment changes,
 * rather than accumulating a new row every review, since there is no
 * "history of ops reviews" feature requested -- just ops's current rating,
 * averaged against the customer aggregate (see DriverRatingService).
 * createdBy/updatedBy (from AuditableEntity) already records which employee
 * set/last changed it -- no separate "ratedBy" field needed.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "driver_ops_rating", uniqueConstraints = @UniqueConstraint(columnNames = { "org_id", "driver_id" }))
public class DriverOpsRating extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String driverId;

	@Column(nullable = false)
	private Integer stars;

	@Column(length = 1000)
	private String comment;
}

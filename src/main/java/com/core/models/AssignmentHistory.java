package com.core.models;

import com.core.models.embedded.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Phase C -- one immutable row per REAL driver/vehicle reassignment on a
 * duty (BookingService.reAllotDuty, when the incoming driverId and/or
 * fleetVehicleId actually differ from the duty's current values). The
 * initial assignment (BookingService.allotDuty, REQUESTED -> ALLOTTED) does
 * NOT create a row here: at that point there is no previous driver/vehicle
 * to preserve (both are null going in, by construction -- allotDuty only
 * runs on a REQUESTED duty), and no existing "initial assignment history"
 * concept was found anywhere in the codebase to extend instead
 * (DutyAllottedEvent/DutyReAllottedEvent are notification-only records with
 * no "previous" fields and no persistence listener -- verified, not
 * assumed). "Existing assignment changes" is the actual trigger, matching
 * the task's own framing, not "any write to these two columns."
 *
 * changed-by / changed-at are deliberately NOT separate fields: this
 * entity's own inherited createdAt/createdBy (AuditableEntity, populated by
 * the same AuditorAwareImpl/SecurityContextHolder-backed mechanism every
 * other entity in this app already uses) already mean exactly that for a
 * row that is only ever created once, never updated. Reusing them avoids a
 * denormalized duplicate of information JPA auditing already captures.
 *
 * `reason` has no existing reliable source: AllotDutyCommand carries no
 * reason field anywhere in the allot/reallot workflow (grep-verified across
 * the booking DTO package) and no reason-capture UI exists yet. Left
 * nullable and unset by this phase rather than fabricated -- see the
 * service-layer comment in BookingService.reAllotDuty.
 *
 * Every field name is given an explicit snake_case @Column override rather
 * than left to the (nonexistent) physical naming strategy: this app has no
 * Hibernate physical naming strategy configured, so an unannotated field's
 * physical column is its literal camelCase Java name -- confirmed
 * empirically in Phase B against DeviceToken/PurchaseInvoice, and visible
 * again here in BookingEntry's own idx_booking_entry_driver_status index
 * (columnList "driver_id, status" against an unannotated `driverId` field,
 * whose real physical column -- per BookingEntry's own
 * @JoinColumn(name="driverId",...) shadow mapping a few lines below it --
 * is actually "driverId", not "driver_id"). That mismatch is a pre-existing
 * issue outside this phase's scope (reported, not fixed); explicit
 * @Column names on every field declared directly on this entity avoid the
 * same trap for this new table.
 *
 * The one field this entity does NOT declare itself -- createdAt, inherited
 * from AuditableEntity -- is exactly this same trap in miniature, and was
 * caught empirically (a throwaway Hibernate-metadata diagnostic, same
 * technique as Phase B's), not assumed: AuditableEntity's createdAt has no
 * @Column(name=...) override either, so its real physical column is the
 * literal camelCase "createdAt", not "created_at" -- the index below
 * references it as such.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "assignment_history", indexes = {
		@Index(name = "idx_assignment_history_org_duty_created", columnList = "org_id, duty_id, createdAt")
})
public class AssignmentHistory extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", length = 40, nullable = false, updatable = false)
	private String id;

	@Column(name = "org_id", length = 40, nullable = false, updatable = false)
	private String orgId;

	@Column(name = "booking_id", length = 40, nullable = false, updatable = false)
	private String bookingId;

	@Column(name = "duty_id", length = 40, nullable = false, updatable = false)
	private String dutyId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_entry_id", nullable = false, updatable = false)
	private BookingEntry bookingEntry;

	@Column(name = "previous_driver_id", length = 40, updatable = false)
	private String previousDriverId;

	@Column(name = "new_driver_id", length = 40, updatable = false)
	private String newDriverId;

	@Column(name = "previous_fleet_vehicle_id", length = 40, updatable = false)
	private String previousFleetVehicleId;

	@Column(name = "new_fleet_vehicle_id", length = 40, updatable = false)
	private String newFleetVehicleId;

	@Column(name = "reason", length = 500, updatable = false)
	private String reason;
}

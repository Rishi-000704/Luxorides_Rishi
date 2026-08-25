package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.FraudSignalStatus;
import com.core.models.enums.FraudSignalType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Real rule-based anomaly signal, computed from real booking/cancellation/GPS
 * data (see FraudSignalService). Purely a review-queue record -- creating one
 * NEVER blocks a booking, a client, or a driver automatically. An employee
 * reviews and dismisses or acts on it manually, matching the same
 * human-in-the-loop pattern used for refund approval.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fraud_signal")
public class FraudSignal extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private FraudSignalType type;

	@Column(length = 40)
	private String clientId;

	@Column(length = 40)
	private String driverId;

	@Column(length = 40)
	private String bookingId;

	@Column(length = 40)
	private String dutyId;

	@Column(nullable = false, length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FraudSignalStatus status;

	private String reviewedBy;

	private Instant reviewedAt;
}

package com.core.models;

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
 * One rating per completed duty, submitted by the client who owns the
 * booking. Unique on duty_id -- a duty can be rated exactly once, matching
 * how the customer app prompts for a rating right after completion.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "trip_rating", uniqueConstraints = @UniqueConstraint(columnNames = "duty_id"))
public class TripRating extends AuditableEntity {

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

	@Column(nullable = false, length = 40)
	private String clientId;

	@Column(length = 40)
	private String driverId;

	@Column(nullable = false)
	private Integer stars;

	@Column(length = 1000)
	private String comment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_entry_id", nullable = false)
	private BookingEntry bookingEntry;
}

package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.NotificationRecipientType;

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
 * Real, backend-persisted in-app notification feed. Populated by
 * NotificationEventListener off the same domain events the realtime
 * booking-status WS channel already broadcasts -- this is purely an
 * additive read of those events, never a new publisher. Actual push
 * delivery (FcmPushService) is a separate, best-effort concern layered on
 * top of this row; the in-app feed exists and works regardless of whether
 * push is configured.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification")
public class Notification extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationRecipientType recipientType;

	@Column(nullable = false, length = 40)
	private String recipientId;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 500)
	private String body;

	@Column(nullable = false, length = 40)
	private String type;

	@Column(length = 40)
	private String bookingId;

	@Column(length = 40)
	private String dutyId;

	private Instant readAt;
}

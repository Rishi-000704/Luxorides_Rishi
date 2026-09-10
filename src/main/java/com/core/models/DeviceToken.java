package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.NotificationRecipientType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Phase A -- index added against verified query evidence, not guessed:
 * DeviceTokenRepository.findByOrgIdAndRecipientTypeAndRecipientId is the
 * only lookup this repository does besides findByToken (already covered by
 * the unique constraint below) and deleteAllByTokenIn (also token-keyed).
 * findByOrgIdAndRecipientTypeAndRecipientId runs on every single
 * NotificationService.create call (i.e. every push send) and previously had
 * no covering index -- a full table scan of device_token per send.
 *
 * columnList uses the literal (camelCase) property names, not snake_case:
 * this app has no Hibernate physical naming strategy configured, so
 * unannotated @Column fields keep their Java property name as the physical
 * column name verbatim (confirmed empirically via Hibernate's own resolved
 * Table/Index/Column metadata -- @Index.columnList is not run back through
 * any naming strategy, so it must already name the real physical column).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "device_token",
		uniqueConstraints = @UniqueConstraint(columnNames = "token"),
		indexes = @Index(name = "idx_device_token_org_recipient", columnList = "orgId, recipientType, recipientId"))
public class DeviceToken extends AuditableEntity {

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

	@Column(nullable = false, length = 512, unique = true)
	private String token;

	@Column(length = 20)
	private String platform;
}

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "device_token", uniqueConstraints = @UniqueConstraint(columnNames = "token"))
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

package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.ReminderPriority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reminder extends AuditableEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;
	@Column(length = 40)
	private String orgId;
	@Column(length = 40)
	private String referenceId;
	@Column(length = 100)
	private String title;
	@Column(length = 500)
	private String description;
	private Instant reminderDate;
	private Instant activationDate;
	private ReminderPriority priority;
	@Column(length = 100)
	private String status;
	private Boolean recurring;
	@Column(length = 100)
	private String recurrenceInterval;
}
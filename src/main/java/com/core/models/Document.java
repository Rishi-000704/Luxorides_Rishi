package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.DocumentVerificationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Document extends AuditableEntity {
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
	@Column(length = 100)
	private String documentType;
	private Instant issueDate;
	private Instant expiryDate;
	@Column(length = 500)
	private String description;
	@Column(length = 80)
	private String fileName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DocumentVerificationStatus status = DocumentVerificationStatus.PENDING_REVIEW;

	@Column(length = 500)
	private String rejectionReason;

	private Instant verifiedAt;

	@Column(length = 40)
	private String verifiedBy;
}

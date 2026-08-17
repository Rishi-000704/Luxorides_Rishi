package com.core.models;

import java.time.Instant;

import com.core.util.PhoneNumberNormalizer;
import com.core.validation.ValidPhone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Table(name = "user_otp")
@Data
public class UserOtp {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false)
	private String id;

	@Column(name = "phone", nullable = false, length = 20)
	@NotBlank
	@ValidPhone
	private String phone;

	@Column(name = "otp_hash", nullable = false, length = 255)
	private String otpHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	@PreUpdate
	protected void beforeSave() {

		// Normalize phone on both create & update
		this.phone = PhoneNumberNormalizer.normalize(this.phone);

		// Only on create
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
			this.expiresAt = this.createdAt.plusSeconds(10 * 60);
		}
	}

	public boolean isExpired() {
		return Instant.now().isAfter(this.expiresAt);
	}

}

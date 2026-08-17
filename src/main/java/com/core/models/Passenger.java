package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.Name;
import com.core.util.PhoneNumberNormalizer;
import com.core.validation.ValidPhone;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(indexes = { @Index(name = "idx_passenger_client", columnList = "client_id"),
		@Index(name = "idx_passenger_org", columnList = "org_id") })
public class Passenger extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String clientId;

	@Embedded
	private Name name;

	@Column(length = 15)
	@ValidPhone
	private String phone;

	@Column(length = 50)
	private String email;

	@PrePersist
	@PreUpdate
	private void normalizePhones() {
		// Optional phone
		if (this.phone != null && !this.phone.isBlank()) {
			this.phone = PhoneNumberNormalizer.normalize(this.phone);
		} else {
			this.phone = null;
		}
	}
}

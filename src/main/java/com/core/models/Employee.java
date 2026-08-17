package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.util.PhoneNumberNormalizer;
import com.core.validation.ValidPhone;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Employee extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String userId;

	@Embedded
	private Name name;

	@Column(unique = true, length = 100)
	private String email;

	@Column(unique = true, length = 15, nullable = false)
	@NotBlank
	@ValidPhone
	private String phone;
	
	@Embedded
	private DisplayAddress address;

	@Column(length = 50)
	private String pic;
	
	@PrePersist
	@PreUpdate
	private void normalizePhones() {

		// Required phone
		this.phone = PhoneNumberNormalizer.normalize(this.phone);
	}
}
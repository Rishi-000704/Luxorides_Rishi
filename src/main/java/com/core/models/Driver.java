package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.models.enums.OwnershipType;
import com.core.util.PhoneNumberNormalizer;
import com.core.validation.ValidPhone;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Driver extends AuditableEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;
	@Column(nullable = false, length = 40)
	private String orgId;
	@Column(length = 40)
	private String clientId;
	@Column(length = 40)
	private String userId;
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "salutation", column = @Column(name = "name_salutation", length = 10)),
			@AttributeOverride(name = "firstName", column = @Column(name = "name_first", length = 50, nullable = false)),
			@AttributeOverride(name = "lastName", column = @Column(name = "name_last", length = 50)) })
	private Name name;
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "salutation", column = @Column(name = "father_salutation", length = 10)),
			@AttributeOverride(name = "firstName", column = @Column(name = "father_first", length = 50)),
			@AttributeOverride(name = "lastName", column = @Column(name = "father_last", length = 50)) })
	private Name fatherName;
	@Column(nullable = false, length = 20)
	private String gender;
	@Column(nullable = false, length = 15)
	@NotBlank
	@ValidPhone
	private String phone;
	@Column(length = 15)
	@ValidPhone
	private String alternatePhone;
	@Embedded
	private DisplayAddress address;
	@Column(length = 12)
	private String adharNumber;
	@Column(length = 20)
	private String licenseNumber;
	@Column(length = 80)
	private String pic;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private OwnershipType ownership;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
	@JoinColumn(name = "clientId", referencedColumnName = "id", insertable = false, updatable = false)
	private Client client;

	@PrePersist
	@PreUpdate
	private void normalizePhones() {

		// Required phone
		this.phone = PhoneNumberNormalizer.normalize(this.phone);

		// Optional phone
		if (this.alternatePhone != null && !this.alternatePhone.isBlank()) {
			this.alternatePhone = PhoneNumberNormalizer.normalize(this.alternatePhone);
		} else {
			this.alternatePhone = null;
		}
	}
}

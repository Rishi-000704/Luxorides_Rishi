package com.core.models;

import com.core.models.embedded.DisplayAddress;
import com.core.models.enums.OrgStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Org {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false)
	private String id;

	@Column(nullable = false, length = 40, unique = true)
	private String orgId;

	@Column(nullable = false, length = 100)
	private String orgName;

	@Embedded
	private DisplayAddress address;

	@Column(nullable = false, length = 12)
	private String pan;
	@Column(nullable = false, length = 20)
	private String cin;
	@Column(nullable = false, length = 15)
	private String gstin;
	@Column(nullable = false, length = 15)
	private String phone;
	@Column(length = 15)
	private String alternatePhone;
	@Column(nullable = false, length = 50)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrgStatus status;

	@Column(length = 100)
	private String websiteLink;
	@Column(length = 500)
	private String remarks;
}

package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.DisplayAddress;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrgBillingEntity extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(length = 100)
	private String brandName;

	@Column(nullable = false, length = 200)
	private String legalName;

	@Embedded
	private DisplayAddress address;

	@Column(length = 20)
	private String cin;

	@Column(nullable = false, length = 15)
	private String gstin;

	@Column(length = 15)
	private String phone;

	@Column(length = 15)
	private String alternatePhone;

	@Column(length = 50)
	private String email;

	@Column(length = 50)
	private String businessType;

	@Column(nullable = false, length = 100)
	private String bankName;

	@Column(nullable = false, length = 100)
	private String accountName;

	@Column(nullable = false, length = 50)
	private String accountNumber;

	@Column(nullable = false, length = 50)
	private String ifsc;

	@Column(nullable = false, length = 50)
	private String upiId;

	@Column(length = 100)
	private String logo;

	@Lob
	@Column(columnDefinition = "LONGTEXT")
	private String termsAndConditions;

	@Column(nullable = false)
	private Integer gstRate;
}
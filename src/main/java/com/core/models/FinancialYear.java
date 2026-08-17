package com.core.models;

import java.time.Instant;

import com.core.models.embedded.AuditableEntity;

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
@AllArgsConstructor
@NoArgsConstructor
public class FinancialYear extends AuditableEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;
	@Column(nullable = false, length = 40)
	private String orgId;
	@Column(nullable = false, length = 40)
	private String orgBillingEntityId;
	private Instant startDate;
	private Instant endDate;
	@Column(nullable = false, length = 10)
	private String invoicePrefix;
	private Integer invoiceCounter;

}

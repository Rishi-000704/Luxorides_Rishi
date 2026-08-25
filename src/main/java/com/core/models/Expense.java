package com.core.models;

import java.math.BigDecimal;
import java.time.Instant;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.ExpenseCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Real operating-expense entry (fuel, maintenance, insurance, salary, ...),
 * admin-entered -- there was no expense tracking anywhere in the codebase
 * before this (confirmed by prior research: the system only tracked
 * revenue). The revenue/expense dashboard's net-profit figure is only ever
 * computed from rows that actually exist here, never estimated.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "expense")
public class Expense extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ExpenseCategory category;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private Instant incurredAt;

	@Column(length = 40)
	private String fleetVehicleId;

	@Column(length = 40)
	private String driverId;

	@Column(length = 500)
	private String remarks;
}

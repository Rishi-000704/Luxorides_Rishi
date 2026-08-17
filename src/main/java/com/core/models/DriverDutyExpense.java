package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.Money;
import com.core.models.enums.DriverDutyExpenseStatus;
import com.core.models.enums.DriverDutyExpenseType;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
	name = "driver_duty_expense",
	indexes = {
		@Index(name = "idx_driver_expense_duty", columnList = "duty_id"),
		@Index(name = "idx_driver_expense_entry", columnList = "booking_entry_id")
	}
)
public class DriverDutyExpense extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String dutyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DriverDutyExpenseType expenseType;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "amount", column = @Column(name = "amount", precision = 15, scale = 2, nullable = false)),
		@AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3, nullable = false))
	})
	private Money amount;

	@Column(length = 300)
	private String description;

	@Column(length = 120)
	private String receiptPhoto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DriverDutyExpenseStatus status = DriverDutyExpenseStatus.DRIVER_SUBMITTED;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_entry_id", nullable = false)
	private BookingEntry bookingEntry;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "checkpoint_id")
	private DriverDutyCheckpoint checkpoint;
}
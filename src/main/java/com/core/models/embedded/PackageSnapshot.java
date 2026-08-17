package com.core.models.embedded;

import com.core.models.enums.DutyType;
import com.core.models.enums.PackageScope;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PackageSnapshot {

	/* Identity (reference only, not FK logic) */
	@Column(length = 40)
	private String packageId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PackageScope scope;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DutyType dutyType;

	private Integer time;
	private Integer distance;

	@Column(length = 20)
	private String unit;

	/* Pricing snapshot */
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "base_fare_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "base_fare_currency", length = 3, nullable = false)) })
	private Money baseFare;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "extra_per_km_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "extra_per_km_currency", length = 3, nullable = false)) })
	private Money extraPerKM;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "extra_per_hs_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "extra_per_hs_currency", length = 3, nullable = false)) })
	private Money extraPerHS;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "night_charge_amount", precision = 15, scale = 2, nullable = false)),
			@AttributeOverride(name = "currency", column = @Column(name = "night_charge_currency", length = 3, nullable = false)) })
	private Money nightCharge;
}

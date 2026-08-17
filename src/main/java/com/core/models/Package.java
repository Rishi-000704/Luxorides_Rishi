package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.Money;
import com.core.models.enums.DutyType;
import com.core.models.enums.PackageScope;

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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Package extends AuditableEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;
	
	@Column(nullable = false, length = 40)
	private String orgId;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PackageScope scope;
	
	@Column(length = 40)
	private String clientId;
	
	@Column(nullable = false, length = 40)
	private String masterVehicleId;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DutyType dutyType;

	private Integer time;

	@Column(length = 20)
	private String unit;

	private Integer distance;
	
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "amount", column = @Column(name = "base_fare_amount", precision = 15, scale = 2, nullable = false)),
		@AttributeOverride(name = "currency", column = @Column(name = "base_fare_currency", length = 3, nullable = false))
	})
	private Money baseFare;
	
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "amount", column = @Column(name = "extra_per_km_amount", precision = 15, scale = 2, nullable = false)),
		@AttributeOverride(name = "currency", column = @Column(name = "extra_per_km_currency", length = 3, nullable = false))
	})
	private Money extraPerKM;
	
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "amount", column = @Column(name = "extra_per_hs_amount", precision = 15, scale = 2, nullable = false)),
		@AttributeOverride(name = "currency", column = @Column(name = "extra_per_hs_currency", length = 3, nullable = false))
	})
	private Money extraPerHS;
	
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "amount", column = @Column(name = "night_charge_amount", precision = 15, scale = 2, nullable = false)),
		@AttributeOverride(name = "currency", column = @Column(name = "night_charge_currency", length = 3, nullable = false))
	})
	private Money nightCharge;
	
	@Column(nullable = false)
	private Boolean forSales = true;
	
	@Column(length = 100)
	private String location;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clientId", referencedColumnName = "id", insertable = false, updatable = false)
	private Client client;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "masterVehicleId", referencedColumnName = "id", insertable = false, updatable = false)
	private MasterVehicle masterVehicle;
}

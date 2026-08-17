package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.VehicleStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@NoArgsConstructor
@AllArgsConstructor
public class MasterVehicle extends AuditableEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;
	@Column(nullable = false, length = 40)
	private String orgId;
	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 100)
	private String pic;

	@Column(length = 50)
	private String fuelSystem;
	@Column(length = 50)
	private String fuelConsumption;
	@Column(length = 50)
	private String vehicleColor;
	@Column(length = 50)
	private String category;
	@Column(length = 50)
	private String brand;
	@Column(length = 50)
	private String seats;
	@Column(length = 50)
	private String doors;
	@Column(length = 50)
	private String transmissionType;
	@Column(length = 50)
	private String horsePower;
	@Column(length = 50)
	private String vehicleClass;
	@Column(length = 10)
	private String modelYear;
	@Column(length = 50)
	private String performance;
	private Integer dimension_length;
	private Integer dimension_width;
	private Integer dimension_height;
	private Integer dimension_wheelbase;
	@Column(length = 100)
	private String slug;
	private Integer rating;
	private Integer popularity;
	private Boolean chauffeurDriven;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VehicleStatus status;

	private String remarks;
}

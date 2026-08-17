package com.core.dtos.vehicle;

import java.time.Instant;

import com.core.models.enums.VehicleStatus;

public record MasterVehicleDTO(

		String id, String orgId,

		String name, String pic,

		String fuelSystem, String fuelConsumption, String vehicleColor, String category, String brand, String seats,
		String doors, String transmissionType, String horsePower, String vehicleClass, String modelYear,
		String performance,

		Integer dimensionLength, Integer dimensionWidth, Integer dimensionHeight, Integer dimensionWheelbase,

		String slug, Integer rating, Integer popularity, Boolean chauffeurDriven,

		VehicleStatus status, String remarks,

		Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
}

package com.core.dtos.client.app;

import java.util.List;

import com.core.models.embedded.Money;

public record VehicleCatalogDTO(String id, String name, String pic, String fuelSystem, String fuelConsumption,
		String vehicleColor, String category, String brand, String seats, String doors, String transmissionType,
		String horsePower, String vehicleClass, String modelYear, String performance, Integer dimension_length,
		Integer dimension_width, Integer dimension_height, Integer dimension_wheelbase, String slug, Integer rating,
		Integer popularity, Boolean chauffeurDriven,Money startingPrice, List<Package> packages) {
	public record Package(String packageId, String dutyType, // including time, unit and distance
			Money baseFare, Money extraPerKM, Money extraPerHS, Money nightCharge) {
	}
}

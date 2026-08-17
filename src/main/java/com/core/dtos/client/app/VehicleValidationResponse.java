package com.core.dtos.client.app;

public record VehicleValidationResponse(
		
		String vehicleId,

		VehicleCatalogDTO.Package selectedPackage,

		String warning) {

}

package com.core.dtos.analytics;

public record VehicleUtilizationResponse(
		String fleetVehicleId,
		String vehicleName,
		String registrationNumber,
		long completedDuties,
		long totalDistanceKm
) {
}

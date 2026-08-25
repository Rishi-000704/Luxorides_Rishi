package com.core.dtos.maintenance;

public record MaintenancePredictionResponse(
		String fleetVehicleId,
		String vehicleName,
		String registrationNumber,
		Integer currentOdometerKm,
		Integer kmSinceLastService,
		Long daysSinceLastService,
		boolean dueForService,
		boolean overdue
) {
}

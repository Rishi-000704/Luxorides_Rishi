package com.core.dtos.dispatch;

public record DispatchVehicleSuggestion(
		String fleetVehicleId,
		String vehicleName,
		String registrationNumber,
		Double idleDays,
		double score
) {
}

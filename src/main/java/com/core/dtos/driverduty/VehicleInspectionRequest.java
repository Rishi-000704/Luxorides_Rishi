package com.core.dtos.driverduty;

import com.core.models.enums.CleanlinessRating;
import com.core.models.enums.FuelLevel;
import com.core.models.enums.VehicleConditionRating;

public record VehicleInspectionRequest(
		VehicleConditionRating exteriorCondition,
		VehicleConditionRating interiorCondition,
		String damageNotes,
		CleanlinessRating cleanliness,
		VehicleConditionRating tyreCondition,
		VehicleConditionRating lightsCondition,
		FuelLevel fuelLevel,
		boolean driverConfirmed
) {
}

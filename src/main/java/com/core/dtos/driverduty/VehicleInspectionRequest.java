package com.core.dtos.driverduty;

import com.core.models.enums.VehicleConditionRating;

public record VehicleInspectionRequest(
		VehicleConditionRating exteriorCondition,
		VehicleConditionRating interiorCondition,
		String damageNotes
) {
}

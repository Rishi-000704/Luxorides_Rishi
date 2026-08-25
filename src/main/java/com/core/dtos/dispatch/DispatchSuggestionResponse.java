package com.core.dtos.dispatch;

import java.util.List;

public record DispatchSuggestionResponse(
		List<DispatchDriverSuggestion> drivers,
		List<DispatchVehicleSuggestion> vehicles
) {
}

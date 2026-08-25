package com.core.dtos.maintenance;

import java.math.BigDecimal;
import java.time.Instant;

public record VehicleMaintenanceRecordRequest(
		String fleetVehicleId,
		String serviceType,
		Instant serviceDate,
		Integer odometerKmAtService,
		BigDecimal cost,
		String remarks
) {
}

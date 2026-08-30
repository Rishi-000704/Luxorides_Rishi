package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutyAcceptanceResponse(String dutyId, boolean accepted, Instant acceptedAt) {
}

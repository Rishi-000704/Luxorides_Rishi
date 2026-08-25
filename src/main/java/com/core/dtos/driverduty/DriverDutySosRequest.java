package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutySosRequest(Double latitude, Double longitude, Instant capturedAt, String notes) {
}

package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutyArrivalResponse(boolean success, Instant arrivedAt) {
}

package com.core.dtos.driverduty;

import java.time.Instant;

public record DriverDutyDeclineResponse(String dutyId, boolean declined, Instant declinedAt, String reason) {
}

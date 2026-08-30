package com.core.dtos.driverduty;

import java.time.Instant;

public record CloseDutyConfirmationResponse(boolean closed, Instant closedAt) {
}

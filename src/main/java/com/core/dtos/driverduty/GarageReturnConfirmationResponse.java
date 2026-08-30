package com.core.dtos.driverduty;

import java.time.Instant;

public record GarageReturnConfirmationResponse(boolean confirmed, Instant confirmedAt) {
}

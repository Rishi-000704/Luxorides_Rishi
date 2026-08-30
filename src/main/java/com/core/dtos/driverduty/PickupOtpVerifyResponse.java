package com.core.dtos.driverduty;

import java.time.Instant;

public record PickupOtpVerifyResponse(boolean verified, Instant verifiedAt) {
}

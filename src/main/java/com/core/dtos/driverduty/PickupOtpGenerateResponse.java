package com.core.dtos.driverduty;

public record PickupOtpGenerateResponse(boolean sent, int expiresInSeconds, boolean alreadyVerified) {
}

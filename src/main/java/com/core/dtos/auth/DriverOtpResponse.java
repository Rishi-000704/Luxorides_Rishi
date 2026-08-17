package com.core.dtos.auth;

public record DriverOtpResponse(boolean success, String message, Integer expiresInSeconds) {
}

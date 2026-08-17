package com.core.dtos.auth;

public record ClientOtpResponse(boolean success, String message, Integer expiresInSeconds) {
}

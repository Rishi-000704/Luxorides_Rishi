package com.core.dtos.config;

public record UpdatePasswordRequest(String oldPassword, String verifyOldPassword,
		String newPassword) {
}

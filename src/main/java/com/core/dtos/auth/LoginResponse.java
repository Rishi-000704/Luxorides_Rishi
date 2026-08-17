package com.core.dtos.auth;

import org.springframework.beans.factory.annotation.Value;

import lombok.Data;

@Data
public class LoginResponse {
	private String token;

	@Value("${security.jwt.expiration-time}")
	private long expiresIn;
}

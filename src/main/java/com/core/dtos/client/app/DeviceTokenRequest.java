package com.core.dtos.client.app;

public record DeviceTokenRequest(
		String token,
		String platform
) {
}

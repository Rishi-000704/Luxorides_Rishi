package com.core.config;

import java.util.Arrays;
import java.util.List;

/*
 * Shared parsing for the cors.allowed-origins property (comma-separated
 * exact origins, e.g. "https://customer.example.com,https://fleetovo.example.com")
 * -- used identically by SecurityConfiguration's HTTP CORS bean and
 * WebSocketConfig's handshake origin restriction, so both stay driven by
 * the one property instead of two independently-maintained origin lists.
 */
public final class CorsOrigins {

	private CorsOrigins() {
	}

	public static List<String> parse(String allowedOrigins) {
		return Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toList();
	}
}

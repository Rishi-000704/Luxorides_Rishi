package com.core.dtos.client.app;

import java.time.Instant;

public record TripShareLinkResponse(
		String token,
		Instant expiresAt
) {
}

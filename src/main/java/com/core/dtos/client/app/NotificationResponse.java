package com.core.dtos.client.app;

import java.time.Instant;

public record NotificationResponse(
		String id,
		String title,
		String body,
		String type,
		String bookingId,
		String dutyId,
		Instant createdAt,
		Instant readAt
) {
}

package com.core.dtos.client.app;

import java.util.List;

public record NotificationSummaryResponse(
		long unreadCount,
		List<NotificationResponse> notifications
) {
}

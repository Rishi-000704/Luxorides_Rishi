package com.core.dtos.client.app;

import java.time.Instant;
import java.util.List;

import com.core.models.enums.SupportTicketStatus;

public record SupportTicketResponse(
		String id,
		String subject,
		SupportTicketStatus status,
		Instant createdAt,
		List<Message> messages
) {
	public record Message(
			String id,
			String senderType,
			String message,
			Instant createdAt
	) {
	}
}

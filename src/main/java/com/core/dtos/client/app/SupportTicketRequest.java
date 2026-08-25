package com.core.dtos.client.app;

public record SupportTicketRequest(
		String subject,
		String message
) {
}

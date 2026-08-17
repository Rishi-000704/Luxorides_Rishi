package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.RefundInitiatedEvent;
import com.core.services.notification.RefundInitiatedNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefundInitiatedListener {

	private final RefundInitiatedNotificationService notificationService;

	@Async
	@EventListener
	public void handle(RefundInitiatedEvent event) {
		notificationService.notify(event);
	}
}

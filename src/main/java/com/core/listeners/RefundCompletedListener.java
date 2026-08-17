package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.RefundCompletedEvent;
import com.core.services.notification.RefundCompletedNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefundCompletedListener {

	private final RefundCompletedNotificationService notificationService;

	@Async
	@EventListener
	public void handle(RefundCompletedEvent event) {
		notificationService.notify(event);
	}
}

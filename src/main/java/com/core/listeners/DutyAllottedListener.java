package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.DutyAllottedEvent;
import com.core.services.notification.DutyAllotmentNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyAllottedListener {

	private final DutyAllotmentNotificationService notificationService;

	@Async
	@EventListener
	public void handle(DutyAllottedEvent event) {
		notificationService.notify(event);
	}
}

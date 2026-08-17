package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.DutyClosedEvent;
import com.core.services.notification.DutyClosureNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyClosedListener {

	private final DutyClosureNotificationService notificationService;

	@Async
	@EventListener
	public void handle(DutyClosedEvent event) {
		notificationService.notify(event);
	}
}

package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.DutyReClosedEvent;
import com.core.services.notification.DutyReClosureNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyReClosedListener {

	private final DutyReClosureNotificationService notificationService;

	@Async
	@EventListener
	public void handle(DutyReClosedEvent event) {
		notificationService.notify(event);
	}
}

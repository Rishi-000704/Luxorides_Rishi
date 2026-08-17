package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.DutyReAllottedEvent;
import com.core.services.notification.DutyReAllotmentNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyReAllottedListener {

	private final DutyReAllotmentNotificationService notificationService;

	@Async
	@EventListener
	public void handle(DutyReAllottedEvent event) {
		notificationService.notify(event);
	}
}

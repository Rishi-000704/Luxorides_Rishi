package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.BookingCancelledEvent;
import com.core.services.notification.BookingCancellationNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingCancelledListener {

	private final BookingCancellationNotificationService notificationService;

	@Async
	@EventListener
	public void handle(BookingCancelledEvent event) {
		notificationService.notify(event);
	}
}

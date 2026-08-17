package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.BookingConfirmedEvent;
import com.core.services.notification.BookingConfirmationNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingConfirmedListener {

	private final BookingConfirmationNotificationService notificationService;

	@Async
	@EventListener
	public void handle(BookingConfirmedEvent event) {
		notificationService.notify(event);
	}
}

package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.PaymentPendingEvent;
import com.core.services.notification.PaymentPendingNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentPendingListener {

	private final PaymentPendingNotificationService notificationService;

	@Async
	@EventListener
	public void handle(PaymentPendingEvent event) {
		notificationService.notify(event);
	}
}

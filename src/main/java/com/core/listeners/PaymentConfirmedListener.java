package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.PaymentConfirmedEvent;
import com.core.services.notification.PaymentConfirmationNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentConfirmedListener {

	private final PaymentConfirmationNotificationService notificationService;

	@Async
	@EventListener
	public void handle(PaymentConfirmedEvent event) {
		notificationService.notify(event);
	}
}

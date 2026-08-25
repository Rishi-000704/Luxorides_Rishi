package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.PaymentConfirmedEvent;
import com.core.ws.DutyPaymentChannelRegistry;

import lombok.RequiredArgsConstructor;

/*
 * Only fires for driver-duty QR payments -- PaymentEventAssembler leaves
 * dutyId null for ordinary checkout/manual payments, so this is a no-op
 * for every other PaymentConfirmedEvent.
 */
@Component
@RequiredArgsConstructor
public class RealtimeDutyPaymentBroadcastListener {

	private final DutyPaymentChannelRegistry registry;

	@Async
	@EventListener
	public void handle(PaymentConfirmedEvent event) {
		if (event.dutyId() == null) {
			return;
		}

		registry.broadcast(event.dutyId(), true, "PAID");
	}
}

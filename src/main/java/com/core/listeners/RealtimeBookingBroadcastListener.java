package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.BookingBilledEvent;
import com.core.events.BookingCancelledEvent;
import com.core.events.BookingCompletedEvent;
import com.core.events.BookingConfirmedEvent;
import com.core.events.DutyAllottedEvent;
import com.core.events.DutyClosedEvent;
import com.core.events.DutyEntryCompletedEvent;
import com.core.events.DutyReAllottedEvent;
import com.core.events.DutyReClosedEvent;
import com.core.events.DutyStartedEvent;
import com.core.events.PaymentConfirmedEvent;
import com.core.ws.BookingChannelRegistry;

import lombok.RequiredArgsConstructor;

/*
 * Pushes a minimal {bookingId, event} signal to any customer-app WebSocket
 * subscribed to that booking, for every event that can change what the
 * booking detail screen shows. The client re-fetches the same REST detail
 * endpoint it already uses (ClientBookingService.get) on receipt -- this
 * listener never pushes full booking state itself, so there is only one
 * place that defines the booking payload shape.
 */
@Component
@RequiredArgsConstructor
public class RealtimeBookingBroadcastListener {

	private final BookingChannelRegistry registry;

	@Async
	@EventListener
	public void handle(DutyAllottedEvent event) {
		registry.broadcast(event.bookingId(), "DUTY_ALLOTTED");
	}

	@Async
	@EventListener
	public void handle(DutyReAllottedEvent event) {
		registry.broadcast(event.bookingId(), "DUTY_REALLOTTED");
	}

	@Async
	@EventListener
	public void handle(DutyClosedEvent event) {
		registry.broadcast(event.bookingId(), "DUTY_CLOSED");
	}

	@Async
	@EventListener
	public void handle(DutyReClosedEvent event) {
		registry.broadcast(event.bookingId(), "DUTY_RECLOSED");
	}

	@Async
	@EventListener
	public void handle(DutyStartedEvent event) {
		registry.broadcast(event.bookingId(), "DUTY_STARTED");
	}

	@Async
	@EventListener
	public void handle(BookingCompletedEvent event) {
		registry.broadcast(event.bookingId(), "BOOKING_COMPLETED");
	}

	@Async
	@EventListener
	public void handle(BookingCancelledEvent event) {
		registry.broadcast(event.bookingId(), "BOOKING_CANCELLED");
	}

	@Async
	@EventListener
	public void handle(PaymentConfirmedEvent event) {
		registry.broadcast(event.bookingId(), "PAYMENT_CONFIRMED");
	}

	@Async
	@EventListener
	public void handle(BookingConfirmedEvent event) {
		registry.broadcast(event.bookingId(), "BOOKING_CONFIRMED");
	}

	@Async
	@EventListener
	public void handle(DutyEntryCompletedEvent event) {
		registry.broadcast(event.bookingId(), "DUTY_ENTRY_COMPLETED");
	}

	@Async
	@EventListener
	public void handle(BookingBilledEvent event) {
		registry.broadcast(event.bookingId(), "BOOKING_BILLED");
	}
}

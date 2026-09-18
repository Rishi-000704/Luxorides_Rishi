package com.core.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.core.events.BookingBilledEvent;
import com.core.events.BookingCancelledEvent;
import com.core.events.BookingConfirmedEvent;
import com.core.events.DutyAllottedEvent;
import com.core.events.DutyEntryCompletedEvent;
import com.core.events.DutyStartedEvent;
import com.core.events.PaymentConfirmedEvent;
import com.core.models.Booking;
import com.core.models.enums.NotificationRecipientType;
import com.core.repositories.BookingRepository;
import com.core.services.NotificationService;

import lombok.RequiredArgsConstructor;

/*
 * Populates the real in-app notification feed off the same domain events
 * RealtimeBookingBroadcastListener already broadcasts over WebSocket --
 * purely additive, no publisher changes. None of these events carry the
 * client's row id directly (only bookingId/orgId), so each handler resolves
 * it via the booking, same as ClientBookingService does elsewhere.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationService notificationService;
	private final BookingRepository bookingRepository;

	@Async
	@EventListener
	public void handle(BookingConfirmedEvent event) {
		notify(event.orgId(), event.bookingId(), null, "Booking confirmed",
				"Your booking " + event.bookingId() + " has been confirmed.", "BOOKING_CONFIRMED");
	}

	@Async
	@EventListener
	public void handle(DutyAllottedEvent event) {
		String message = event.driverName() != null
				? event.driverName() + " has been assigned to your trip."
				: "A driver has been assigned to your trip.";

		notify(event.orgId(), event.bookingId(), event.dutyId(), "Driver assigned", message, "DUTY_ALLOTTED");

		if (event.driverId() != null) {
			notificationService.create(
					event.orgId(), NotificationRecipientType.DRIVER, event.driverId(),
					"New duty assigned", "You've been assigned duty " + event.dutyId() + ".",
					"DUTY_ALLOTTED", event.bookingId(), event.dutyId());
		}
	}

	@Async
	@EventListener
	public void handle(DutyStartedEvent event) {
		notify(event.orgId(), event.bookingId(), event.dutyId(), "Trip started",
				"Your driver has started the trip.", "DUTY_STARTED");
	}

	@Async
	@EventListener
	public void handle(DutyEntryCompletedEvent event) {
		notify(event.orgId(), event.bookingId(), event.dutyId(), "Trip completed",
				"Your trip has been completed.", "DUTY_ENTRY_COMPLETED");
	}

	@Async
	@EventListener
	public void handle(BookingBilledEvent event) {
		notify(event.orgId(), event.bookingId(), null, "Invoice ready",
				"Your invoice for booking " + event.bookingId() + " is ready.", "BOOKING_BILLED");
	}

	@Async
	@EventListener
	public void handle(BookingCancelledEvent event) {
		notify(event.orgId(), event.bookingId(), null, "Booking cancelled",
				"Your booking " + event.bookingId() + " has been cancelled.", "BOOKING_CANCELLED");
	}

	@Async
	@EventListener
	public void handle(PaymentConfirmedEvent event) {
		notify(event.orgId(), event.bookingId(), event.dutyId(), "Payment received",
				"We received your payment of " + event.amountPaid() + ".", "PAYMENT_CONFIRMED");
	}

	private void notify(String orgId, String bookingId, String dutyId, String title, String body, String type) {
		bookingRepository.findByBookingIdAndOrgId(bookingId, orgId)
				.map(Booking::getClientId)
				.ifPresent(clientId -> notificationService.create(
						orgId, NotificationRecipientType.CLIENT, clientId, title, body, type, bookingId, dutyId));
	}
}

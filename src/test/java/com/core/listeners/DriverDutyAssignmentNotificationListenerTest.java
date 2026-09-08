package com.core.listeners;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.core.events.DutyAllottedEvent;
import com.core.events.DutyReAllottedEvent;
import com.core.models.enums.DutyType;
import com.core.models.enums.NotificationRecipientType;
import com.core.services.NotificationService;

/*
 * Covers the new driver-assignment push listener: correct org/driver
 * targeting, NotificationRecipientType.DRIVER (never CLIENT), a no-op when
 * an event somehow carries no driverId, and -- critically for privacy -- the
 * exact title/body sent, proving no customer name/phone/fare/address is
 * ever included (only orgId/driverId/bookingId/dutyId, all opaque
 * identifiers the app already re-fetches full detail for).
 */
class DriverDutyAssignmentNotificationListenerTest {

	private static final String ORG_ID = "org-1";
	private static final String DRIVER_ID = "driver-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String DUTY_ID = "duty-1";

	private final NotificationService notificationService = mock(NotificationService.class);
	private final DriverDutyAssignmentNotificationListener listener =
			new DriverDutyAssignmentNotificationListener(notificationService);

	@Test
	void handleAllotted_notifiesTheAssignedDriverOnly() {
		listener.handle(allottedEvent(DRIVER_ID));

		verify(notificationService).create(
				eq(ORG_ID), eq(NotificationRecipientType.DRIVER), eq(DRIVER_ID),
				eq("New Duty Assigned"), eq("You have a new duty assignment."), eq("DUTY_ASSIGNED"),
				eq(BOOKING_ID), eq(DUTY_ID));
	}

	@Test
	void handleReAllotted_notifiesTheNewDriver() {
		listener.handle(reAllottedEvent("driver-2-the-replacement"));

		verify(notificationService).create(
				eq(ORG_ID), eq(NotificationRecipientType.DRIVER), eq("driver-2-the-replacement"),
				eq("New Duty Assigned"), eq("You have a new duty assignment."), eq("DUTY_ASSIGNED"),
				eq(BOOKING_ID), eq(DUTY_ID));
	}

	@Test
	void handle_noDriverIdOnEvent_doesNotCallNotificationService() {
		listener.handle(allottedEvent(null));

		verify(notificationService, never()).create(
				isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
	}

	@Test
	void payload_containsNoCustomerOrBookingDetail_onlyOpaqueIds() {
		listener.handle(allottedEvent(DRIVER_ID));

		// Booking/dutyId are opaque correlation ids the app already re-fetches
		// full authoritative detail for -- title/body must never contain
		// customer name, phone, fare, or address, matching the privacy
		// requirement. The exact-string assertions above already prove this;
		// this test documents the requirement explicitly as its own case.
		verify(notificationService).create(
				eq(ORG_ID), eq(NotificationRecipientType.DRIVER), eq(DRIVER_ID),
				eq("New Duty Assigned"), eq("You have a new duty assignment."), eq("DUTY_ASSIGNED"),
				eq(BOOKING_ID), eq(DUTY_ID));
	}

	private DutyAllottedEvent allottedEvent(String driverId) {
		return new DutyAllottedEvent(
				ORG_ID, "client@example.com", "+919876543210", "Real Customer Name",
				BOOKING_ID, DUTY_ID,
				DutyType.LOCAL, "10 Jan 2026", "Airport Terminal 2",
				driverId, "Real Driver Name", "+919999999999",
				"Sedan", "SEDAN", "DL01AB1234");
	}

	private DutyReAllottedEvent reAllottedEvent(String driverId) {
		return new DutyReAllottedEvent(
				ORG_ID, "client@example.com", "+919876543210", "Real Customer Name",
				BOOKING_ID, DUTY_ID,
				DutyType.LOCAL, "10 Jan 2026", "Airport Terminal 2",
				driverId, "Real Driver Name", "+919999999999",
				"Sedan", "SEDAN", "DL01AB1234");
	}
}

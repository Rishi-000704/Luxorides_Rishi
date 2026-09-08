package com.core.listeners;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.core.events.DutyAllottedEvent;
import com.core.events.DutyReAllottedEvent;
import com.core.models.enums.NotificationRecipientType;
import com.core.services.NotificationService;

import lombok.RequiredArgsConstructor;

/*
 * Wakes the newly-assigned driver's Chauffeur app with a real push
 * notification -- the missing link the dispatch-discovery audit identified.
 * This is only ever a wake/inform signal: the payload carries no
 * booking/customer detail, and the app is required to re-fetch authoritative
 * state (GET /driver/app/duties/active) after receiving it rather than
 * trusting anything here.
 *
 * Deliberately AFTER_COMMIT (unlike the existing CLIENT-facing
 * NotificationEventListener/DutyAllottedListener, which use plain @Async
 * @EventListener): a push must never be sent for an allotment that ultimately
 * rolls back. @Async keeps the FCM call off the committing thread. The event
 * records carry only primitive/String fields (no lazy entity references), so
 * there's nothing to break by running after the originating
 * transaction/session has already closed.
 */
@Component
@RequiredArgsConstructor
public class DriverDutyAssignmentNotificationListener {

	private static final String TITLE = "New Duty Assigned";
	private static final String BODY = "You have a new duty assignment.";

	private final NotificationService notificationService;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(DutyAllottedEvent event) {
		notify(event.orgId(), event.driverId(), event.bookingId(), event.dutyId());
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(DutyReAllottedEvent event) {
		notify(event.orgId(), event.driverId(), event.bookingId(), event.dutyId());
	}

	private void notify(String orgId, String driverId, String bookingId, String dutyId) {
		if (driverId == null) {
			return;
		}

		notificationService.create(
				orgId, NotificationRecipientType.DRIVER, driverId,
				TITLE, BODY, "DUTY_ASSIGNED",
				bookingId, dutyId);
	}
}

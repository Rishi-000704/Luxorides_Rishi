package com.core.events;

public record DutyEntryCompletedEvent(String bookingId, String dutyId, String orgId) {
}

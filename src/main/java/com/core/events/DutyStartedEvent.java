package com.core.events;

public record DutyStartedEvent(String bookingId, String dutyId, String orgId) {
}

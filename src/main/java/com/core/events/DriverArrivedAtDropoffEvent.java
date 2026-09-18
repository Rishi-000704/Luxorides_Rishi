package com.core.events;

public record DriverArrivedAtDropoffEvent(String bookingId, String dutyId, String orgId) {
}

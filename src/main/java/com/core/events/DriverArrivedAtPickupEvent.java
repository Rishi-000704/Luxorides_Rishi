package com.core.events;

public record DriverArrivedAtPickupEvent(String bookingId, String dutyId, String orgId) {
}

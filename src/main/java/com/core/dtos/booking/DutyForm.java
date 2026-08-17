package com.core.dtos.booking;

import java.time.Instant;
import java.util.List;

import com.core.dtos.common.AddressSnapshotDTO;

public record DutyForm(String bookingId, String dutyId, List<String> passengerIds, Instant reportingTime,
		AddressSnapshotDTO reportingLocation, AddressSnapshotDTO dropLocation, Instant dropTime,
		String requestedVehicleId, String flightNumber, String packageId, String clientNotes) {
}

package com.core.dtos.client.app;

import java.time.Instant;
import java.util.List;

import com.core.dtos.common.AddressSnapshotDTO;

public record ClientBookingDraftDTO(

		String clientBillingEntityId,

		List<Entry> entries

) {

	public record Entry(

			String vehicleId, String packageId,

			AddressSnapshotDTO reportingLocation, Instant reportingTime,

			AddressSnapshotDTO dropLocation, Instant dropTime,

			String flightNumber, List<String> passengerIds,

			String clientNotes) {
	}

}

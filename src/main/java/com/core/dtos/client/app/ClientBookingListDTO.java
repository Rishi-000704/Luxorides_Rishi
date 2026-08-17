package com.core.dtos.client.app;

import java.time.Instant;

import com.core.models.enums.BookingStatus;

public record ClientBookingListDTO(

		String bookingId,

		String vehicle, // first / primary vehicle name
		String location, // first duty reporting location
		Instant from, // first duty reporting time
		Instant till,

		BookingStatus status,

		Integer duties // number of entries

) {
}

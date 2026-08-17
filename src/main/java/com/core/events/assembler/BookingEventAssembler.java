package com.core.events.assembler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.core.events.BookingCancelledEvent;
import com.core.events.BookingConfirmedEvent;
import com.core.models.Booking;
import com.core.util.DateFormatUtil;

@Component
public class BookingEventAssembler {

	public BookingConfirmedEvent toBookingConfirmedEvent(Booking booking) {

		List<BookingConfirmedEvent.Duty> duties = booking.getEntries().stream()
				.map(entry -> new BookingConfirmedEvent.Duty(entry.getDutyId(), entry.getRequestedVehicle().getName(),
						DateFormatUtil.display(entry.getReportingTime()), entry.getReportingLocation().getFormattedAddress()))
				.toList();

		return new BookingConfirmedEvent(
				booking.getOrgId(),
				booking.getClient().getEmail(),
				booking.getClient().getPhone(),
				booking.getClient().getName().getDisplayName(),
				booking.getBookingId(),
				booking.getTotal().getAmount().toString(),
				duties.size(),
				duties
		);
	}

	public BookingCancelledEvent toBookingCancelledEvent(Booking booking, String cancellationReason) {

		List<BookingCancelledEvent.Duty> duties = booking.getEntries().stream()
				.map(e -> new BookingCancelledEvent.Duty(e.getDutyId(), e.getRequestedVehicle().getName(),
						DateFormatUtil.display(e.getReportingTime()), e.getReportingLocation().getFormattedAddress()))
				.toList();

		return new BookingCancelledEvent(
				booking.getOrgId(),
				booking.getClient().getEmail(),
				booking.getClient().getPhone(),
				booking.getClient().getName().getDisplayName(),
				booking.getBookingId(),
				booking.getTotal().getAmount().toString(),
				cancellationReason,
				duties.size(),
				duties
		);
	}
}

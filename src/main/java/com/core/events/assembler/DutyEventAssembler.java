package com.core.events.assembler;

import org.springframework.stereotype.Component;

import com.core.events.DutyAllottedEvent;
import com.core.events.DutyClosedEvent;
import com.core.events.DutyReAllottedEvent;
import com.core.events.DutyReClosedEvent;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.util.DateFormatUtil;

@Component
public class DutyEventAssembler {

	public DutyAllottedEvent toDutyAllottedEvent(Booking booking, BookingEntry entry) {

		return new DutyAllottedEvent(
				booking.getOrgId(),
				booking.getClient().getEmail(), booking.getClient().getPhone(),
				booking.getClient().getName().getDisplayName(),

				booking.getBookingId(), entry.getDutyId(),

				entry.getPack().getDutyType(), DateFormatUtil.display(entry.getReportingTime()),
				entry.getReportingLocation().getFormattedAddress(),

				entry.getDriver().getName().getDisplayName(), entry.getDriver().getPhone(),

				entry.getAllotedVehicle().getMasterVehicle().getName(),
				entry.getAllotedVehicle().getMasterVehicle().getCategory(),
				entry.getAllotedVehicle().getRegistrationNumber());
	}

	public DutyReAllottedEvent toDutyReAllottedEvent(Booking booking, BookingEntry entry) {

		return new DutyReAllottedEvent(
				booking.getOrgId(),
				booking.getClient().getEmail(), booking.getClient().getPhone(),
				booking.getClient().getName().getDisplayName(),

				booking.getBookingId(), entry.getDutyId(),

				entry.getPack().getDutyType(), DateFormatUtil.display(entry.getReportingTime()),
				entry.getReportingLocation().getFormattedAddress(),

				entry.getDriver().getName().getDisplayName(), entry.getDriver().getPhone(),

				entry.getAllotedVehicle().getMasterVehicle().getName(),
				entry.getAllotedVehicle().getMasterVehicle().getCategory(),
				entry.getAllotedVehicle().getRegistrationNumber());
	}

	public DutyClosedEvent toDutyClosedEvent(Booking booking, BookingEntry entry) {

		return new DutyClosedEvent(
				booking.getOrgId(),
				booking.getClient().getEmail(), booking.getClient().getPhone(),
				booking.getClient().getName().getDisplayName(),

				booking.getBookingId(), entry.getDutyId(),

				DateFormatUtil.display(entry.getStartAt()), DateFormatUtil.display(entry.getReportingTime()),
				DateFormatUtil.display(entry.getDropTime()), DateFormatUtil.display(entry.getEndAt()),

				String.valueOf(entry.getClosingKM() - entry.getStartingKM()));
	}

	public DutyReClosedEvent toDutyReClosedEvent(Booking booking, BookingEntry entry) {

		return new DutyReClosedEvent(
				booking.getOrgId(),
				booking.getClient().getEmail(), booking.getClient().getPhone(),
				booking.getClient().getName().getDisplayName(),

				booking.getBookingId(), entry.getDutyId(),

				DateFormatUtil.display(entry.getStartAt()), DateFormatUtil.display(entry.getReportingTime()),
				DateFormatUtil.display(entry.getDropTime()), DateFormatUtil.display(entry.getEndAt()),

				String.valueOf(entry.getClosingKM() - entry.getStartingKM()));
	}
}

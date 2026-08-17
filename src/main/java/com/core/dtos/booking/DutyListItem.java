package com.core.dtos.booking;

import java.time.Instant;

import com.core.dtos.common.MoneyDTO;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.Driver;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.embedded.Money;
import com.core.models.embedded.Name;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;

public record DutyListItem(

		/* ================= CORE ================= */
		String dutyId,
		String bookingId,
		BookingStatus bookingStatus,
		DutyStatus status,

		/* ================= CLIENT ================= */
		String clientId,
		String clientName,
		String clientPhone,

		/* ================= VEHICLE REQUEST ================= */
		String requestedVehicleId,
		String requestedVehicleName,

		/* ================= ALLOTMENT ================= */
		String fleetVehicleId,
		String allotedVehicleName,
		String allotedVehicleNumber,

		String driverId,
		String driverName,
		String driverPhone,

		String supplierId,
		String supplierName,

		/* ================= DUTY DETAILS ================= */
		Instant reportingTime,
		String reportingLocation,

		Instant dropTime,
		String dropLocation,

		String flightNumber,
		MoneyDTO dutyTotal,

		/* ================= AUDIT ================= */
		Instant createdAt,
		Instant updatedAt
) {

	public static DutyListItem from(BookingEntry e) {

		Booking booking = e.getBooking();
		Client client = booking != null ? booking.getClient() : null;

		MasterVehicle requestedVehicle = e.getRequestedVehicle();
		FleetVehicle allotedVehicle = e.getAllotedVehicle();
		MasterVehicle allotedMasterVehicle = allotedVehicle != null ? allotedVehicle.getMasterVehicle() : null;

		Driver driver = e.getDriver();
		Client supplier = e.getSupplier();

		return new DutyListItem(

				e.getDutyId(),
				booking != null ? booking.getBookingId() : null,
				booking != null ? booking.getStatus() : null,
				e.getStatus(),

				client != null ? client.getId() : null,
				client != null ? displayName(client.getName()) : null,
				client != null ? client.getPhone() : null,

				e.getMasterVehicleId(),
				requestedVehicle != null ? requestedVehicle.getName() : null,

				e.getFleetVehicleId(),
				allotedMasterVehicle != null ? allotedMasterVehicle.getName() : null,
				allotedVehicle != null ? allotedVehicle.getRegistrationNumber() : null,

				e.getDriverId(),
				driver != null ? displayName(driver.getName()) : null,
				driver != null ? driver.getPhone() : null,

				e.getSupplierId(),
				supplier != null ? displayName(supplier.getName()) : null,

				e.getReportingTime(),
				e.getReportingLocation() != null ? e.getReportingLocation().getFormattedAddress() : null,

				e.getDropTime(),
				e.getDropLocation() != null ? e.getDropLocation().getFormattedAddress() : null,

				e.getFlightNumber(),
				toMoneyDTO(e.getDutyTotal()),

				e.getCreatedAt(),
				e.getUpdatedAt()
		);
	}

	private static String displayName(Name name) {
		return name == null ? null : name.getDisplayName();
	}

	private static MoneyDTO toMoneyDTO(Money money) {
		return money == null ? null : new MoneyDTO(money.getAmount(), money.getCurrency());
	}
}
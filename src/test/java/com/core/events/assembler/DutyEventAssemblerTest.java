package com.core.events.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.core.events.DutyAllottedEvent;
import com.core.events.DutyReAllottedEvent;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.Driver;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.Name;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;

/*
 * Phase 2 dispatch fix: DutyAllottedEvent/DutyReAllottedEvent previously
 * carried only driver display strings, so nothing driver-facing could target
 * a real recipient. Confirms the assembler now populates the real driverId
 * (BookingEntry.driverId, the authoritative field allotDuty/reAllotDuty set)
 * for both the initial allotment and a re-allotment to a DIFFERENT driver --
 * the exact case that matters for "notify the new driver, not the old one".
 */
class DutyEventAssemblerTest {

	private static final String ORG_ID = "org-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String DUTY_ID = "duty-1";

	private final DutyEventAssembler assembler = new DutyEventAssembler();

	@Test
	void toDutyAllottedEvent_carriesRealDriverId() {
		BookingEntry entry = entry("driver-1");

		DutyAllottedEvent event = assembler.toDutyAllottedEvent(entry.getBooking(), entry);

		assertEquals("driver-1", event.driverId());
		assertEquals(ORG_ID, event.orgId());
		assertEquals(BOOKING_ID, event.bookingId());
		assertEquals(DUTY_ID, event.dutyId());
	}

	@Test
	void toDutyReAllottedEvent_carriesNewDriverId_notPreviousDriver() {
		// Simulates BookingService.reAllotDuty: the entry's driverId/driver are
		// already the NEW driver by the time the event is assembled -- the
		// previous driver never appears anywhere in this event.
		BookingEntry entry = entry("driver-2-the-replacement");

		DutyReAllottedEvent event = assembler.toDutyReAllottedEvent(entry.getBooking(), entry);

		assertEquals("driver-2-the-replacement", event.driverId());
	}

	private BookingEntry entry(String driverId) {
		Client client = new Client();
		client.setEmail("client@example.com");
		client.setPhone("+919876543210");
		client.setName(new Name(null, "Test", "Client"));

		Booking booking = new Booking();
		booking.setOrgId(ORG_ID);
		booking.setBookingId(BOOKING_ID);
		booking.setClient(client);
		booking.setStatus(BookingStatus.CONFIRMED);

		Driver driver = new Driver();
		driver.setId(driverId);
		driver.setOrgId(ORG_ID);
		driver.setPhone("+919999999999");
		driver.setName(new Name(null, "Test", "Driver"));

		MasterVehicle masterVehicle = new MasterVehicle();
		masterVehicle.setName("Sedan");
		masterVehicle.setCategory("SEDAN");

		FleetVehicle fleetVehicle = new FleetVehicle();
		fleetVehicle.setRegistrationNumber("DL01AB1234");
		fleetVehicle.setMasterVehicle(masterVehicle);

		PackageSnapshot pack = new PackageSnapshot();

		BookingEntry entry = new BookingEntry();
		entry.setDutyId(DUTY_ID);
		entry.setBooking(booking);
		entry.setStatus(DutyStatus.ALLOTTED);
		entry.setPack(pack);
		entry.setReportingLocation(new AddressSnapshot("Airport", null, 12.97, 77.59));
		entry.setDriverId(driverId);
		entry.setDriver(driver);
		entry.setFleetVehicleId("vehicle-1");
		entry.setAllotedVehicle(fleetVehicle);

		return entry;
	}
}

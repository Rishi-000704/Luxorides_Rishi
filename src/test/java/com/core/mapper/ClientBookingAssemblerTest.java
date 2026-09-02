package com.core.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.client.app.ClientBookingDTO;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.embedded.Name;
import com.core.models.enums.OwnershipType;
import com.core.repositories.TripRatingRepository;
import com.core.services.common.FileAccessTokenService;

/*
 * P1.4 -- covers ClientBookingAssembler.mapEntry, previously 1 + 2N
 * (tripRatingRepository.findAverageStarsByDriverIdAndOrgId +
 * countByDriverIdAndOrgId, both per duty entry within one booking).
 * Now one batch call for every distinct driver across the booking.
 */
class ClientBookingAssemblerTest {

	private static final String ORG_ID = "org-1";

	private TripRatingRepository tripRatingRepository;
	private ClientBookingAssembler assembler;

	@BeforeEach
	void setUp() {
		tripRatingRepository = mock(TripRatingRepository.class);
		FileAccessTokenService fileAccessTokenService = mock(FileAccessTokenService.class);

		assembler = new ClientBookingAssembler(tripRatingRepository, fileAccessTokenService);
	}

	private Driver driver(String id, String phone) {
		Driver d = new Driver();
		d.setId(id);
		d.setOrgId(ORG_ID);
		d.setPhone(phone);
		d.setGender("MALE");
		d.setOwnership(OwnershipType.ORG);
		d.setName(new Name("Mr.", "Test", "Driver"));
		return d;
	}

	private BookingEntry entry(String dutyId, Booking booking, Driver driver) {
		BookingEntry e = new BookingEntry();
		e.setDutyId(dutyId);
		e.setBooking(booking);
		e.setStatus(DutyStatus.COMPLETED);
		e.setDriver(driver);
		return e;
	}

	private Booking booking(BookingEntry... entries) {
		Booking b = new Booking();
		b.setBookingId("BK-1");
		b.setOrgId(ORG_ID);
		b.setStatus(BookingStatus.COMPLETED);
		b.setEntries(new ArrayList<>(List.of(entries)));
		b.setPayments(new ArrayList<>());
		return b;
	}

	@Test
	void toDTO_batchesRatingLookup_forAllEntriesDrivers_insteadOfOnePerEntry() {
		Booking b = new Booking();
		b.setBookingId("BK-1");
		b.setOrgId(ORG_ID);
		b.setStatus(BookingStatus.COMPLETED);
		b.setPayments(new ArrayList<>());

		Driver d1 = driver("d1", "+911111111111");
		Driver d2 = driver("d2", "+912222222222");

		List<BookingEntry> entries = new ArrayList<>(List.of(entry("duty-1", b, d1), entry("duty-2", b, d2)));
		b.setEntries(entries);

		when(tripRatingRepository.aggregateStarsByDriverIds(eq(ORG_ID), anyCollection())).thenReturn(List.<Object[]>of(
				new Object[] { "d1", 4.8, 12L }));

		ClientBookingDTO dto = assembler.toDTO(b);

		verify(tripRatingRepository).aggregateStarsByDriverIds(eq(ORG_ID), anyCollection());
		verify(tripRatingRepository, never()).findAverageStarsByDriverIdAndOrgId(any(), any());
		verify(tripRatingRepository, never()).countByDriverIdAndOrgId(any(), any());

		ClientBookingDTO.Entry entry1 = dto.entries().stream().filter(e -> e.dutyId().equals("duty-1")).findFirst().orElseThrow();
		assertEquals(4.8, entry1.driverRatingAverage());
		assertEquals(12L, entry1.driverRatingCount());

		// d2 has no ratings -- GROUP BY omitted it entirely.
		ClientBookingDTO.Entry entry2 = dto.entries().stream().filter(e -> e.dutyId().equals("duty-2")).findFirst().orElseThrow();
		assertNull(entry2.driverRatingAverage());
		assertEquals(0L, entry2.driverRatingCount());
	}

	@Test
	void toDTO_multipleEntriesSameDriver_onlyOneDistinctIdInBatchCall() {
		Booking b = booking();
		Driver d1 = driver("d1", "+911111111111");
		List<BookingEntry> entries = new ArrayList<>(List.of(
				entry("duty-1", b, d1), entry("duty-2", b, d1), entry("duty-3", b, d1)));
		b.setEntries(entries);

		when(tripRatingRepository.aggregateStarsByDriverIds(eq(ORG_ID), anyCollection()))
				.thenReturn(List.<Object[]>of(new Object[] { "d1", 5.0, 3L }));

		ClientBookingDTO dto = assembler.toDTO(b);

		verify(tripRatingRepository).aggregateStarsByDriverIds(eq(ORG_ID), anyCollection());
		assertEquals(3, dto.entries().size());
		dto.entries().forEach(e -> {
			assertEquals(5.0, e.driverRatingAverage());
			assertEquals(3L, e.driverRatingCount());
		});
	}

	@Test
	void toDTO_entryWithNoDriverAssigned_skipsRatingSafely() {
		Booking b = booking();
		BookingEntry e = entry("duty-1", b, null);
		b.setEntries(new ArrayList<>(List.of(e)));

		ClientBookingDTO dto = assembler.toDTO(b);

		verify(tripRatingRepository, never()).aggregateStarsByDriverIds(any(), any());
		assertNull(dto.entries().get(0).driverRatingAverage());
		assertEquals(0L, dto.entries().get(0).driverRatingCount());
	}

	@Test
	void toDTO_emptyEntries_doesNotCallBatch() {
		Booking b = booking();
		b.setEntries(new ArrayList<>());

		ClientBookingDTO dto = assembler.toDTO(b);

		assertEquals(0, dto.entries().size());
		verify(tripRatingRepository, never()).aggregateStarsByDriverIds(any(), any());
	}
}

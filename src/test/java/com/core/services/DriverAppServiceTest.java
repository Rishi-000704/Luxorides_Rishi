package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.core.dtos.driverduty.DriverAppDutyTokenResponse;
import com.core.dtos.driverduty.DriverDutyLinkResponse;
import com.core.exception.BusinessException;
import com.core.exception.NotFoundException;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverRepository;

/*
 * Covers ownership scoping on the new /driver/app/** self-service surface, and
 * that duty EXECUTION tokens are minted by delegating to the existing,
 * unmodified ExternalDriverDutyService rather than any new token logic.
 */
class DriverAppServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String USER_ID = "user-1";
	private static final String DRIVER_ID = "driver-1";
	private static final String DUTY_ID = "duty-1";
	private static final String BOOKING_ID = "booking-1";

	private BookingEntryRepository bookingEntryRepository;
	private DriverRepository driverRepository;
	private ExternalDriverDutyService externalDriverDutyService;
	private DriverAppService service;
	private Driver driver;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		driverRepository = mock(DriverRepository.class);
		externalDriverDutyService = mock(ExternalDriverDutyService.class);
		service = new DriverAppService(bookingEntryRepository, driverRepository, externalDriverDutyService);

		driver = new Driver();
		driver.setId(DRIVER_ID);
		driver.setOrgId(ORG_ID);
		driver.setUserId(USER_ID);
		when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.of(driver));
	}

	@Test
	@SuppressWarnings("null")
	void getActiveDuties_scopesToCallingDriverOnly() {
		Pageable pageable = PageRequest.of(0, 10);
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		when(bookingEntryRepository.findActiveDutiesForDriver(
				eq(ORG_ID), eq(DRIVER_ID), eq(List.of(DutyStatus.ALLOTTED, DutyStatus.RUNNING)),
				eq(List.of(BookingStatus.CONFIRMED, BookingStatus.RUNNING)), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(entry)));

		var result = service.getActiveDuties(ORG_ID, USER_ID, pageable);

		assertEquals(1, result.getTotalElements());
		assertEquals(DUTY_ID, result.getContent().get(0).dutyId());
	}

	@Test
	void issueExecutionToken_rejectsDutyNotBelongingToCaller() {
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class,
				() -> service.issueExecutionToken(ORG_ID, USER_ID, DUTY_ID));
	}

	@Test
	void issueExecutionToken_rejectsCompletedDuty() {
		BookingEntry entry = duty(DutyStatus.COMPLETED);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));

		assertThrows(BusinessException.class,
				() -> service.issueExecutionToken(ORG_ID, USER_ID, DUTY_ID));
	}

	@Test
	void issueExecutionToken_delegatesToExistingLinkService_andExtractsRawToken() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));

		Instant expiry = Instant.now().plusSeconds(3600);
		when(externalDriverDutyService.generateDriverDutyLink(BOOKING_ID, DUTY_ID, ORG_ID)).thenReturn(
				new DriverDutyLinkResponse(DUTY_ID, BOOKING_ID, "Driver Name", "DL01AB1234",
						"https://sandbox.fleetovo.com/extrenal/abc123XYZ", expiry));

		DriverAppDutyTokenResponse response = service.issueExecutionToken(ORG_ID, USER_ID, DUTY_ID);

		assertEquals("abc123XYZ", response.token());
		assertEquals(expiry, response.expiresAt());
	}

	@Test
	void issueExecutionToken_rejectsDutyWhoseBookingHasAlreadyClosed() {
		// Reproduces the real bug: a duty added to an already-invoiced, COMPLETED booking
		// (see BookingService#reopenCompletedBookingAfterDutyAdded) stays ALLOTTED at the
		// duty level even though the booking itself can no longer be executed against.
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		entry.getBooking().setStatus(BookingStatus.COMPLETED);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));

		assertThrows(BusinessException.class,
				() -> service.issueExecutionToken(ORG_ID, USER_ID, DUTY_ID));
	}

	private BookingEntry duty(DutyStatus status) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setStatus(BookingStatus.CONFIRMED);

		BookingEntry entry = new BookingEntry();
		entry.setDutyId(DUTY_ID);
		entry.setBooking(booking);
		entry.setStatus(status);
		return entry;
	}
}

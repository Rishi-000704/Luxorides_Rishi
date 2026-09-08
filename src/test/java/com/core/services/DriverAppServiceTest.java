package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.core.dtos.driverduty.DriverDutyAcceptanceResponse;
import com.core.dtos.driverduty.DriverDutyDeclineRequest;
import com.core.dtos.driverduty.DriverDutyDeclineResponse;
import com.core.dtos.driverduty.DriverDutyLinkResponse;
import com.core.exception.BusinessException;
import com.core.exception.NotFoundException;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.NotificationRecipientType;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
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
	private static final String ENTRY_ID = "entry-1";

	private BookingEntryRepository bookingEntryRepository;
	private DriverRepository driverRepository;
	private ExternalDriverDutyService externalDriverDutyService;
	private DriverDutyCheckpointRepository checkpointRepository;
	private NotificationService notificationService;
	private DriverAppService service;
	private Driver driver;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		driverRepository = mock(DriverRepository.class);
		externalDriverDutyService = mock(ExternalDriverDutyService.class);
		checkpointRepository = mock(DriverDutyCheckpointRepository.class);
		notificationService = mock(NotificationService.class);
		service = new DriverAppService(
				bookingEntryRepository, driverRepository, externalDriverDutyService, checkpointRepository, notificationService);

		driver = new Driver();
		driver.setId(DRIVER_ID);
		driver.setOrgId(ORG_ID);
		driver.setUserId(USER_ID);
		when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.of(driver));
	}

	@Test
	void registerDeviceToken_derivesDriverFromJwt_neverFromClientInput() {
		// The method signature itself has no driverId parameter -- the recipient
		// is always the driver resolved from (orgId, userId) off the JWT, exactly
		// like every other /driver/app/** call in this service.
		service.registerDeviceToken(ORG_ID, USER_ID, "expo-token-abc", "ANDROID");

		verify(notificationService).registerDeviceToken(
				ORG_ID, NotificationRecipientType.DRIVER, DRIVER_ID, "expo-token-abc", "ANDROID");
	}

	@Test
	void registerDeviceToken_rejectsCallerWithNoMatchingDriverRecord() {
		when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class,
				() -> service.registerDeviceToken(ORG_ID, USER_ID, "expo-token-abc", "ANDROID"));
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
	void issueExecutionToken_rejectsAllottedDutyThatHasNotBeenAccepted() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));

		assertThrows(BusinessException.class,
				() -> service.issueExecutionToken(ORG_ID, USER_ID, DUTY_ID));
	}

	@Test
	void issueExecutionToken_delegatesToExistingLinkService_andExtractsRawToken() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		entry.setDriverAcceptedAt(Instant.now());
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

	@Test
	void acceptDuty_setsAcceptedTimestamp_whenAllotted() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));

		DriverDutyAcceptanceResponse response = service.acceptDuty(ORG_ID, USER_ID, DUTY_ID);

		assertEquals(DUTY_ID, response.dutyId());
		assertEquals(true, response.accepted());
		assertEquals(entry.getDriverAcceptedAt(), response.acceptedAt());
	}

	@Test
	void acceptDuty_isIdempotent_onDuplicateAccept() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		Instant firstAccept = Instant.now().minusSeconds(60);
		entry.setDriverAcceptedAt(firstAccept);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));

		DriverDutyAcceptanceResponse response = service.acceptDuty(ORG_ID, USER_ID, DUTY_ID);

		assertEquals(firstAccept, response.acceptedAt());
	}

	@Test
	void acceptDuty_rejectsDutyNotInAllottedState() {
		BookingEntry entry = duty(DutyStatus.RUNNING);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));

		assertThrows(BusinessException.class, () -> service.acceptDuty(ORG_ID, USER_ID, DUTY_ID));
	}

	@Test
	void acceptDuty_rejectsDutyNotBelongingToCaller() {
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () -> service.acceptDuty(ORG_ID, USER_ID, DUTY_ID));
	}

	@Test
	void declineDuty_setsDeclinedTimestampAndReason_whenAllotted() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));

		DriverDutyDeclineResponse response = service.declineDuty(ORG_ID, USER_ID, DUTY_ID, new DriverDutyDeclineRequest("Vehicle unavailable"));

		assertEquals(true, response.declined());
		assertEquals("Vehicle unavailable", response.reason());
	}

	@Test
	void declineDuty_isIdempotent_onDuplicateDecline() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		Instant firstDecline = Instant.now().minusSeconds(60);
		entry.setDriverDeclinedAt(firstDecline);
		entry.setDriverDeclineReason("Health issue");
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));

		DriverDutyDeclineResponse response = service.declineDuty(ORG_ID, USER_ID, DUTY_ID, new DriverDutyDeclineRequest("Different reason"));

		assertEquals(firstDecline, response.declinedAt());
		assertEquals("Health issue", response.reason());
	}

	@Test
	void declineDuty_rejectsBlankReason() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));

		assertThrows(BusinessException.class,
				() -> service.declineDuty(ORG_ID, USER_ID, DUTY_ID, new DriverDutyDeclineRequest("   ")));
	}

	@Test
	void declineDuty_rejectsDutyAlreadyRunning() {
		BookingEntry entry = duty(DutyStatus.RUNNING);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));

		assertThrows(BusinessException.class,
				() -> service.declineDuty(ORG_ID, USER_ID, DUTY_ID, new DriverDutyDeclineRequest("Too late")));
	}

	private BookingEntry duty(DutyStatus status) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setStatus(BookingStatus.CONFIRMED);

		BookingEntry entry = new BookingEntry();
		entry.setId(ENTRY_ID);
		entry.setDutyId(DUTY_ID);
		entry.setBooking(booking);
		entry.setStatus(status);
		return entry;
	}
}

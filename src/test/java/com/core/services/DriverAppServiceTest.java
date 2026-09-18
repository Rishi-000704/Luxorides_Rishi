package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.core.dtos.common.AddressSnapshotDTO;
import com.core.dtos.common.NameDTO;
import com.core.dtos.driver.DriverRatingSummaryResponse;
import com.core.dtos.driver.DriverProfileUpdateRequest;
import com.core.dtos.driverduty.DriverAppDutyTokenResponse;
import com.core.dtos.driverduty.DriverDutyAcceptanceResponse;
import com.core.dtos.driverduty.DriverDutyDeclineRequest;
import com.core.dtos.driverduty.DriverDutyDeclineResponse;
import com.core.dtos.driverduty.DriverDutyLinkResponse;
import com.core.dtos.driverduty.DutyRouteLegResponse;
import com.core.exception.BusinessException;
import com.core.exception.NotFoundException;
import com.core.mapper.DriverAssembler;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.NotificationRecipientType;
import com.core.dtos.driver.DriverGarageOptionDTO;
import com.core.models.CityGarage;
import com.core.models.embedded.AddressSnapshot;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverRepository;
import com.core.services.config.CityGarageService;

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
	private DriverAssembler driverAssembler;
	private DriverDocumentService driverDocumentService;
	private DriverRatingService driverRatingService;
	private CityGarageService cityGarageService;
	private DriverAppService service;
	private Driver driver;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		driverRepository = mock(DriverRepository.class);
		externalDriverDutyService = mock(ExternalDriverDutyService.class);
		checkpointRepository = mock(DriverDutyCheckpointRepository.class);
		notificationService = mock(NotificationService.class);
		driverAssembler = mock(DriverAssembler.class);
		driverDocumentService = mock(DriverDocumentService.class);
		driverRatingService = mock(DriverRatingService.class);
		cityGarageService = mock(CityGarageService.class);
		when(driverDocumentService.areRequiredDocumentsVerified(anyString(), anyString())).thenReturn(true);
		service = new DriverAppService(
				bookingEntryRepository, driverRepository, externalDriverDutyService, checkpointRepository,
				notificationService, driverAssembler, driverDocumentService, driverRatingService, cityGarageService);

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

	// Regression coverage for the "Garage"/"Experience" rows on the driver's
	// own Profile screen -- previously non-interactive display-only text
	// with nothing behind them to save to. Both are now real, persisted
	// Driver fields (see Driver.garageLocation/experienceYears), updatable
	// through this same endpoint the rest of the profile edit already uses.
	//
	// Uses the shared `driver` field set up in @BeforeEach (already wired to
	// driverRepository.findByUserId) rather than a fresh local `Driver`
	// instance -- a fresh local here would never be the object resolveDriver()
	// actually returns, so mutating/asserting on it would silently test
	// nothing. (A prior version of this file had exactly that bug in two
	// tests here; caught when an equivalent new email test failed loudly
	// instead of passing vacuously.)
	@Test
	void updateOwnProfile_persistsGarageLocationAndExperienceYears() {
		DriverProfileUpdateRequest request = new DriverProfileUpdateRequest(
				null, null, null, null, null,
				new AddressSnapshotDTO("Sector 62, Noida", "place-123", 28.62, 77.37),
				7
		);

		service.updateOwnProfile(ORG_ID, USER_ID, request);

		assertEquals("Sector 62, Noida", driver.getGarageLocation().getFormattedAddress());
		assertEquals(28.62, driver.getGarageLocation().getLatitude());
		assertEquals(7, driver.getExperienceYears());
	}

	@Test
	void updateOwnProfile_leavesGarageAndExperienceUntouched_whenOmittedFromRequest() {
		driver.setExperienceYears(4);

		// Only updating name -- garageLocation/experienceYears both null in
		// the request must mean "don't touch", the same convention every
		// other optional field on this endpoint already follows.
		DriverProfileUpdateRequest request = new DriverProfileUpdateRequest(
				new NameDTO(null, "NewFirstName", null),
				null, null, null, null, null, null
		);

		service.updateOwnProfile(ORG_ID, USER_ID, request);

		assertEquals(4, driver.getExperienceYears());
		assertEquals(null, driver.getGarageLocation());
	}

	// Onboarding data-loss fix -- Profile Basics previously saved name/email/
	// experienceYears only to an in-memory mock, discarded on restart. Now
	// persisted through this same real endpoint.
	@Test
	void updateOwnProfile_persistsEmail() {
		DriverProfileUpdateRequest request = new DriverProfileUpdateRequest(
				null, null, null, "driver@example.com", null, null, null
		);

		service.updateOwnProfile(ORG_ID, USER_ID, request);

		assertEquals("driver@example.com", driver.getEmail());
	}

	@Test
	void updateOwnProfile_leavesEmailUntouched_whenOmittedFromRequest() {
		driver.setEmail("existing@example.com");

		DriverProfileUpdateRequest request = new DriverProfileUpdateRequest(
				new NameDTO(null, "NewFirstName", null),
				null, null, null, null, null, null
		);

		service.updateOwnProfile(ORG_ID, USER_ID, request);

		assertEquals("existing@example.com", driver.getEmail());
	}

	// Onboarding data-loss fix -- Garage Location previously showed 3
	// hardcoded fake garage names, saved nowhere real. Now backed by the same
	// real CityGarage config ops manages, exposed read-only to the driver.
	@Test
	void getGarages_mapsRealCityGaragesForTheCallingDriversOrg() {
		AddressSnapshot location = new AddressSnapshot();
		location.setFormattedAddress("Sector 62, Noida");
		location.setLatitude(28.62);
		location.setLongitude(77.37);
		CityGarage garage = new CityGarage("garage-1", ORG_ID, "Noida", location);
		when(cityGarageService.getList(ORG_ID)).thenReturn(List.of(garage));

		List<DriverGarageOptionDTO> result = service.getGarages(ORG_ID);

		assertEquals(1, result.size());
		assertEquals("garage-1", result.get(0).id());
		assertEquals("Noida", result.get(0).city());
		assertEquals("Sector 62, Noida", result.get(0).garageLocation().formattedAddress());
	}

	@Test
	void getGarages_emptyListWhenOrgHasNoneConfigured() {
		when(cityGarageService.getList(ORG_ID)).thenReturn(List.of());

		List<DriverGarageOptionDTO> result = service.getGarages(ORG_ID);

		assertEquals(0, result.size());
	}

	// Regression coverage for the Activity screen no longer showing earnings:
	// the driver's own rating summary (client + ops combined, see
	// DriverRatingService) is resolved for the calling driver only,
	// delegating the actual combination logic rather than reimplementing it.
	@Test
	void getOwnRating_resolvesCallingDriver_andDelegatesToRatingService() {
		DriverRatingSummaryResponse expected = new DriverRatingSummaryResponse(4.5, 3L, 5, 4.75);
		when(driverRatingService.getRatingSummary(ORG_ID, DRIVER_ID)).thenReturn(expected);

		DriverRatingSummaryResponse response = service.getOwnRating(ORG_ID, USER_ID);

		assertEquals(expected, response);
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
	void getRouteForLeg_scopesToCallingDriverOnly_andDelegatesToExternalService() {
		BookingEntry entry = duty(DutyStatus.ALLOTTED);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));
		DutyRouteLegResponse expected = new DutyRouteLegResponse(
				"PICKUP", true, 5.0, 600L, "OPEN_ROUTE_SERVICE", true, null, null, null);
		when(externalDriverDutyService.getRouteForLeg(entry, "PICKUP")).thenReturn(expected);

		DutyRouteLegResponse response = service.getRouteForLeg(ORG_ID, USER_ID, DUTY_ID, "PICKUP");

		assertEquals(expected, response);
	}

	@Test
	void getRouteForLeg_rejectsDutyNotBelongingToCaller() {
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class,
				() -> service.getRouteForLeg(ORG_ID, USER_ID, DUTY_ID, "PICKUP"));
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

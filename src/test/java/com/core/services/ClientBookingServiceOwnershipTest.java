package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.driverduty.DriverDutyLocationResponse;
import com.core.exception.BusinessException;
import com.core.exception.NotFoundException;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyLiveLocationRepository;
import com.core.repositories.TripRatingRepository;
import com.core.services.config.OrgService;

/*
 * P0 IDOR fix -- getBooking(bookingId, orgId) and the pre-fix getDutyLocation
 * were both org-scoped only, so any authenticated client in the same org
 * could read another client's full booking detail or live vehicle GPS by
 * guessing a bookingId/dutyId. getOwnedBooking and the fixed getDutyLocation
 * now both additionally require the requesting client to own the booking --
 * same ownership check submitRating/getRating/createShareLink already apply.
 */
class ClientBookingServiceOwnershipTest {

	private static final String ORG_ID = "org-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String DUTY_ID = "duty-1";
	private static final String OWNING_CLIENT_ID = "client-owner";
	private static final String OTHER_CLIENT_ID = "client-other";

	private BookingService bookingService;
	private BookingEntryRepository bookingEntryRepository;
	private DriverDutyLiveLocationRepository liveLocationRepository;
	private CancellationPolicyService cancellationPolicyService;
	private OrgService orgService;
	private ClientBookingService service;

	@BeforeEach
	void setUp() {
		bookingService = mock(BookingService.class);
		bookingEntryRepository = mock(BookingEntryRepository.class);
		liveLocationRepository = mock(DriverDutyLiveLocationRepository.class);
		TripRatingRepository tripRatingRepository = mock(TripRatingRepository.class);
		cancellationPolicyService = mock(CancellationPolicyService.class);
		orgService = mock(OrgService.class);

		service = new ClientBookingService(
				bookingService, bookingEntryRepository, liveLocationRepository,
				tripRatingRepository, cancellationPolicyService, orgService);
	}

	private Booking bookingOwnedBy(String clientId) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setClientId(clientId);
		return booking;
	}

	private Booking cancellableBookingOwnedBy(String clientId) {
		Booking booking = bookingOwnedBy(clientId);
		booking.setStatus(com.core.models.enums.BookingStatus.CONFIRMED);
		return booking;
	}

	private BookingEntry entryForBookingOwnedBy(String clientId) {
		BookingEntry entry = new BookingEntry();
		entry.setDutyId(DUTY_ID);
		entry.setBooking(bookingOwnedBy(clientId));
		return entry;
	}

	/* ================= getOwnedBooking (booking detail) ================= */

	@Test
	void getOwnedBooking_ownerRequestsTheirOwnBooking_returnsIt() {
		when(bookingService.getBooking(BOOKING_ID, ORG_ID)).thenReturn(bookingOwnedBy(OWNING_CLIENT_ID));

		Booking result = service.getOwnedBooking(BOOKING_ID, OWNING_CLIENT_ID, ORG_ID);

		assertEquals(BOOKING_ID, result.getBookingId());
	}

	@Test
	void getOwnedBooking_anotherClientInSameOrgRequestsIt_isDenied_notTheOtherClientsBooking() {
		when(bookingService.getBooking(BOOKING_ID, ORG_ID)).thenReturn(bookingOwnedBy(OWNING_CLIENT_ID));

		assertThrows(BusinessException.class,
				() -> service.getOwnedBooking(BOOKING_ID, OTHER_CLIENT_ID, ORG_ID));
	}

	@Test
	void getOwnedBooking_crossOrgAccess_isRejected_bookingNotFoundInThatOrg() {
		when(bookingService.getBooking(BOOKING_ID, "different-org"))
				.thenThrow(new NotFoundException(com.core.exception.ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));

		assertThrows(NotFoundException.class,
				() -> service.getOwnedBooking(BOOKING_ID, OWNING_CLIENT_ID, "different-org"));
	}

	/* ================= getDutyLocation (live GPS) ================= */

	@Test
	void getDutyLocation_ownerRequestsTheirOwnDuty_succeeds() {
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID))
				.thenReturn(Optional.of(entryForBookingOwnedBy(OWNING_CLIENT_ID)));
		when(liveLocationRepository.findByDutyId(DUTY_ID)).thenReturn(Optional.empty());

		Optional<DriverDutyLocationResponse> result = service.getDutyLocation(DUTY_ID, OWNING_CLIENT_ID, ORG_ID);

		assertTrue(result.isEmpty());
		verify(liveLocationRepository).findByDutyId(DUTY_ID);
	}

	@Test
	void getDutyLocation_anotherClientInSameOrgRequestsIt_isDenied_neverLeaksTheOtherClientsGpsPosition() {
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID))
				.thenReturn(Optional.of(entryForBookingOwnedBy(OWNING_CLIENT_ID)));

		assertThrows(BusinessException.class,
				() -> service.getDutyLocation(DUTY_ID, OTHER_CLIENT_ID, ORG_ID));

		verify(liveLocationRepository, never()).findByDutyId(anyString());
	}

	@Test
	void getDutyLocation_dutyDoesNotExistInOrg_isRejected() {
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.empty());

		assertThrows(BusinessException.class,
				() -> service.getDutyLocation(DUTY_ID, OWNING_CLIENT_ID, ORG_ID));
	}

	/*
	 * ================= CANCELLATION regression =================
	 * getOwnedCancellableBooking was refactored to delegate to getOwnedBooking
	 * for the ownership check -- these confirm cancellation ownership
	 * enforcement (already correct before this task) still works exactly as
	 * before the refactor.
	 */

	@Test
	void getCancellationPreview_ownerRequestsTheirOwnBooking_succeeds() {
		when(bookingService.getBooking(BOOKING_ID, ORG_ID)).thenReturn(cancellableBookingOwnedBy(OWNING_CLIENT_ID));
		when(orgService.getOrg(ORG_ID)).thenReturn(new com.core.models.Org());
		when(cancellationPolicyService.evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new CancellationPolicyService.Evaluation(
						true, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
						java.math.BigDecimal.ZERO, 24, java.math.BigDecimal.ZERO));

		var preview = service.getCancellationPreview(BOOKING_ID, OWNING_CLIENT_ID, ORG_ID);

		assertTrue(preview.withinFreeWindow());
	}

	@Test
	void getCancellationPreview_anotherClientInSameOrgRequestsIt_isDenied() {
		when(bookingService.getBooking(BOOKING_ID, ORG_ID)).thenReturn(cancellableBookingOwnedBy(OWNING_CLIENT_ID));

		assertThrows(BusinessException.class,
				() -> service.getCancellationPreview(BOOKING_ID, OTHER_CLIENT_ID, ORG_ID));
	}

	@Test
	void cancelBookingWithPolicy_anotherClientInSameOrgAttemptsToCancelIt_isDenied_neverCancelsTheBooking() {
		when(bookingService.getBooking(BOOKING_ID, ORG_ID)).thenReturn(cancellableBookingOwnedBy(OWNING_CLIENT_ID));

		assertThrows(BusinessException.class,
				() -> service.cancelBookingWithPolicy(BOOKING_ID, OTHER_CLIENT_ID, ORG_ID, "change of plans"));

		verify(bookingService, never()).cancelBooking(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
	}
}

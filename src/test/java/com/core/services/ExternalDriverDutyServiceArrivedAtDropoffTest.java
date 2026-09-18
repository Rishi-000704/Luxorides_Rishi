package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.core.dtos.driverduty.DriverDutyArrivalResponse;
import com.core.events.DriverArrivedAtDropoffEvent;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.enums.DutyStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.BookingRepository;
import com.core.repositories.DriverDutyAccessTokenRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverDutyExpenseRepository;
import com.core.repositories.DriverDutyLiveLocationRepository;
import com.core.repositories.PaymentRepository;
import com.core.services.common.FileService;
import com.core.services.common.SMSService;
import com.core.ws.DutyLocationChannelRegistry;

/*
 * The driver app's "Arrived at Drop-off" button was previously a purely
 * local screen transition -- no backend call at all (MockDutyService, a
 * 500ms local delay). Exercises ExternalDriverDutyService.markArrivedAtDropoff
 * directly, mirroring ExternalDriverDutyServiceArrivedAtPickupTest for the
 * other end of the trip.
 */
class ExternalDriverDutyServiceArrivedAtDropoffTest {

	private static final String ORG_ID = "org-1";
	private static final String OTHER_ORG_ID = "org-2";
	private static final String DUTY_ID = "duty-1";
	private static final String ENTRY_ID = "entry-1";
	private static final String BOOKING_ID = "booking-1";

	private BookingEntryRepository bookingEntryRepository;
	private DriverDutyTokenValidator tokenValidator;
	private ApplicationEventPublisher eventPublisher;
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		tokenValidator = mock(DriverDutyTokenValidator.class);
		eventPublisher = mock(ApplicationEventPublisher.class);

		service = new ExternalDriverDutyService(
				bookingEntryRepository,
				mock(BookingRepository.class),
				mock(DriverDutyAccessTokenRepository.class),
				mock(DriverDutyCheckpointRepository.class),
				mock(DriverDutyExpenseRepository.class),
				mock(FileService.class),
				mock(BookingService.class),
				mock(RazorpayPaymentService.class),
				mock(MockPaymentService.class),
				mock(PaymentGatewayConfigService.class),
				tokenValidator,
				eventPublisher,
				mock(DriverDutyLiveLocationRepository.class),
				mock(DutyLocationChannelRegistry.class),
				mock(FraudSignalService.class),
				new BCryptPasswordEncoder(),
				mock(SMSService.class),
				mock(DriverDocumentService.class),
				mock(LocationService.class),
				mock(ObjectProvider.class),
				mock(PaymentRepository.class),
				mock(PaymentEventAssembler.class)
		);
	}

	private BookingEntry entry(DutyStatus status) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);

		BookingEntry e = new BookingEntry();
		e.setId(ENTRY_ID);
		e.setDutyId(DUTY_ID);
		e.setBooking(booking);
		e.setStatus(status);
		return e;
	}

	private DriverDutyAccessToken token(BookingEntry entry) {
		DriverDutyAccessToken t = new DriverDutyAccessToken();
		t.setOrgId(ORG_ID);
		t.setDutyId(DUTY_ID);
		t.setBookingEntry(entry);
		return t;
	}

	private void stubLockable(BookingEntry entry) {
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));
	}

	@Test
	void marksArrival_setsTimestamp_savesAndPublishesEvent() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(anyString())).thenReturn(token(entry));

		DriverDutyArrivalResponse response = service.markArrivedAtDropoff("raw-token");

		assertEquals(true, response.success());
		assertNotNull(response.arrivedAt());
		assertEquals(response.arrivedAt(), entry.getArrivedAtDropoffAt());
		verify(bookingEntryRepository, times(1)).save(entry);
		verify(eventPublisher, times(1)).publishEvent(
				new DriverArrivedAtDropoffEvent(BOOKING_ID, DUTY_ID, ORG_ID));
	}

	@Test
	void rejectsWhenDutyNotYetStarted() {
		BookingEntry entry = entry(DutyStatus.ALLOTTED);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(anyString())).thenReturn(token(entry));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.markArrivedAtDropoff("raw-token"));

		assertEquals(ErrorCode.DUTY_NOT_RUNNING, ex.getErrorCode());
		verify(bookingEntryRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void rejectsWhenDutyAlreadyCompleted() {
		BookingEntry entry = entry(DutyStatus.COMPLETED);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(anyString())).thenReturn(token(entry));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.markArrivedAtDropoff("raw-token"));

		assertEquals(ErrorCode.DUTY_NOT_RUNNING, ex.getErrorCode());
		verify(bookingEntryRepository, never()).save(any());
	}

	@Test
	void isIdempotent_repeatedTapDoesNotOverwriteOrRepublish() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		Instant firstArrival = Instant.now().minusSeconds(90);
		entry.setArrivedAtDropoffAt(firstArrival);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(anyString())).thenReturn(token(entry));

		DriverDutyArrivalResponse response = service.markArrivedAtDropoff("raw-token");

		assertEquals(true, response.success());
		assertEquals(firstArrival, response.arrivedAt());
		verify(bookingEntryRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	/*
	 * A driver from a different org can never even resolve a valid token for
	 * this duty -- resolveValidToken itself is the org-isolation boundary
	 * (mirrors DriverDutyTokenValidator's real org-scoped lookup). Simulated
	 * here by having the validator reject outright, exactly what happens when
	 * no token row matches both the raw token and the caller's org.
	 */
	@Test
	void unauthorizedDriver_cannotMutateAnotherDrivers_orgsDuty() {
		when(tokenValidator.resolveValidToken(anyString()))
				.thenThrow(new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		assertThrows(NotFoundException.class, () -> service.markArrivedAtDropoff("raw-token"));

		verify(bookingEntryRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	/*
	 * Same boundary, the other common real-world case: a token that resolved
	 * fine belongs to a duty/org pair lockEntryForDutyExecution can't find --
	 * e.g. a token for org-2 being replayed against an org-1-scoped lookup.
	 */
	@Test
	void invalidOrExpiredToken_isRejected() {
		when(tokenValidator.resolveValidToken(anyString()))
				.thenThrow(new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, OTHER_ORG_ID)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () -> service.markArrivedAtDropoff("expired-or-invalid-token"));

		verify(bookingEntryRepository, never()).save(any());
	}
}

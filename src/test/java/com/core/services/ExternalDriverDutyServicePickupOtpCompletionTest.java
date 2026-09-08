package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.core.dtos.driverduty.DriverDutyEndRequest;
import com.core.dtos.driverduty.PickupOtpVerifyRequest;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.FleetovoException;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.DriverDutyAccessToken;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.BookingStatus;
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
 * P0 task 3 -- pickup OTP must be verified (entry.pickupOtpVerifiedAt, the
 * backend's own authoritative record) before a duty can be finally
 * completed. Exercises the exact completion service
 * (ExternalDriverDutyService.completeDutyEntryAndFinalizeBooking, the only
 * place DutyStatus transitions to COMPLETED and the booking is finalized),
 * not a mocked helper standing in for it -- see the class-level comment on
 * that method for why it is the single transaction boundary for the whole
 * unit of work.
 */
class ExternalDriverDutyServicePickupOtpCompletionTest {

	private static final String ORG_ID = "org-1";
	private static final String OTHER_ORG_ID = "org-2";
	private static final String DUTY_ID = "duty-1";
	private static final String ENTRY_ID = "entry-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String CUSTOMER_PHONE = "+919876543210";

	private BookingEntryRepository bookingEntryRepository;
	private DriverDutyCheckpointRepository checkpointRepository;
	private BookingService bookingService;
	private DriverDutyTokenValidator tokenValidator;
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		checkpointRepository = mock(DriverDutyCheckpointRepository.class);
		bookingService = mock(BookingService.class);
		tokenValidator = mock(DriverDutyTokenValidator.class);

		service = new ExternalDriverDutyService(
				bookingEntryRepository,
				mock(BookingRepository.class),
				mock(DriverDutyAccessTokenRepository.class),
				checkpointRepository,
				mock(DriverDutyExpenseRepository.class),
				mock(FileService.class),
				bookingService,
				mock(RazorpayPaymentService.class),
				mock(MockPaymentService.class),
				mock(PaymentGatewayConfigService.class),
				tokenValidator,
				mock(ApplicationEventPublisher.class),
				mock(DriverDutyLiveLocationRepository.class),
				mock(DutyLocationChannelRegistry.class),
				mock(FraudSignalService.class),
				new BCryptPasswordEncoder(),
				mock(SMSService.class),
				mock(LocationService.class),
				mock(ObjectProvider.class),
				mock(PaymentRepository.class),
				mock(PaymentEventAssembler.class)
		);
	}

	// A fully-populated "day/outstation" package so BookingUtil.calculateTotal's
	// COMPLETED-branch (calculateFinalTotal) can run to completion without
	// hitting any of its optional (extra-km/extra-time/night-charge) money
	// fields -- the fixture keeps runningDistance comfortably inside the
	// included daily distance so those branches are never exercised.
	private PackageSnapshot dayPackage() {
		PackageSnapshot pack = new PackageSnapshot();
		pack.setDistance(300);
		pack.setTime(0);
		pack.setUnit("DAY");
		pack.setBaseFare(com.core.models.embedded.Money.INR(BigDecimal.valueOf(1000)));
		pack.setExtraPerKM(com.core.models.embedded.Money.INR(BigDecimal.ZERO));
		pack.setExtraPerHS(com.core.models.embedded.Money.INR(BigDecimal.ZERO));
		pack.setNightCharge(com.core.models.embedded.Money.INR(BigDecimal.ZERO));
		return pack;
	}

	private BookingEntry entry(DutyStatus status, String orgId) {
		Client client = new Client();
		client.setPhone(CUSTOMER_PHONE);

		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(orgId);
		booking.setClient(client);
		booking.setStatus(BookingStatus.CONFIRMED);

		BookingEntry e = new BookingEntry();
		e.setId(ENTRY_ID);
		e.setDutyId(DUTY_ID);
		e.setBooking(booking);
		e.setStatus(status);
		e.setPack(dayPackage());
		e.setStartAt(Instant.now().minusSeconds(3600));
		e.setStartingKM(100);
		return e;
	}

	private DriverDutyAccessToken token(BookingEntry entry, String orgId) {
		DriverDutyAccessToken t = new DriverDutyAccessToken();
		t.setOrgId(orgId);
		t.setDutyId(DUTY_ID);
		t.setBookingEntry(entry);
		return t;
	}

	private void stubLockable(BookingEntry entry, String orgId) {
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, orgId)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));
	}

	private DriverDutyEndRequest endRequest() {
		return new DriverDutyEndRequest(150, null, null, Instant.now(), null, null);
	}

	// ---------------------------------------------------------------
	// PICKUP OTP GATES FINAL COMPLETION
	// ---------------------------------------------------------------

	@Test
	void completeDuty_rejectsWhenPickupOtpNeverVerified() throws Exception {
		BookingEntry entry = entry(DutyStatus.RUNNING, ORG_ID);
		// pickupOtpVerifiedAt left null -- the driver's app skipped/never
		// reached pickup verification (the exact resume-bug scenario).
		stubLockable(entry, ORG_ID);
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(eq(ENTRY_ID), any())).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.completeDutyEntryAndFinalizeBooking(
				token(entry, ORG_ID), endRequest(), null, "odometer.jpg", null, "127.0.0.1", "test-agent"));

		assertEquals(ErrorCode.PICKUP_OTP_NOT_VERIFIED, ex.getErrorCode());
	}

	@Test
	void completeDuty_rejectedCompletion_doesNotPartiallyFinalizeAnything() throws Exception {
		BookingEntry entry = entry(DutyStatus.RUNNING, ORG_ID);
		stubLockable(entry, ORG_ID);
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(eq(ENTRY_ID), any())).thenReturn(false);

		assertThrows(BusinessException.class, () -> service.completeDutyEntryAndFinalizeBooking(
				token(entry, ORG_ID), endRequest(), null, "odometer.jpg", null, "127.0.0.1", "test-agent"));

		// Nothing downstream of the guard may have run.
		assertEquals(DutyStatus.RUNNING, entry.getStatus());
		verify(checkpointRepository, never()).save(any());
		verify(bookingEntryRepository, never()).save(any());
		verify(bookingService, never()).finalizeBookingAfterDutyCompletion(any(), any(), anyBoolean(), any());
	}

	@Test
	void completeDuty_succeeds_whenPickupOtpVerified() throws Exception {
		BookingEntry entry = entry(DutyStatus.RUNNING, ORG_ID);
		entry.setPickupOtpVerifiedAt(Instant.now().minusSeconds(1800));
		stubLockable(entry, ORG_ID);
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(eq(ENTRY_ID), any())).thenReturn(false);

		service.completeDutyEntryAndFinalizeBooking(
				token(entry, ORG_ID), endRequest(), null, "odometer.jpg", null, "127.0.0.1", "test-agent");

		assertEquals(DutyStatus.COMPLETED, entry.getStatus());
		verify(checkpointRepository, times(1)).save(any());
		verify(bookingEntryRepository, times(1)).save(entry);
		verify(bookingService, times(1)).finalizeBookingAfterDutyCompletion(
				eq(BOOKING_ID), eq(DUTY_ID), anyBoolean(), eq(ORG_ID));
	}

	@Test
	void completeDuty_alreadyCompleted_staysRejectedForItsOwnReason_regardlessOfPickupOtp() {
		// Pre-existing idempotent-completion behavior (DUTY_ALREADY_CLOSED) is
		// checked before the new pickup-OTP guard and must still take
		// priority -- unaffected by this change.
		BookingEntry entry = entry(DutyStatus.COMPLETED, ORG_ID);
		stubLockable(entry, ORG_ID);

		BusinessException ex = assertThrows(BusinessException.class, () -> service.completeDutyEntryAndFinalizeBooking(
				token(entry, ORG_ID), endRequest(), null, "odometer.jpg", null, "127.0.0.1", "test-agent"));

		assertEquals(ErrorCode.DUTY_ALREADY_CLOSED, ex.getErrorCode());
	}

	// ---------------------------------------------------------------
	// OTP VERIFICATION ITSELF STAYS DUTY/ORG-SCOPED (structural IDOR check)
	// ---------------------------------------------------------------

	@Test
	void verifyPickupOtp_cannotTargetAnotherOrganizationsDuty() {
		// The entry only exists under ORG_ID; a token minted for a different
		// org can never resolve it, no matter what dutyId/otp is supplied --
		// lockEntryForDutyExecution looks the entry up by the TOKEN's own
		// orgId, never a client-supplied one.
		BookingEntry entry = entry(DutyStatus.RUNNING, ORG_ID);
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.lockById(ENTRY_ID)).thenReturn(Optional.of(entry));
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, OTHER_ORG_ID)).thenReturn(Optional.empty());

		DriverDutyAccessToken foreignToken = token(entry, OTHER_ORG_ID);
		when(tokenValidator.resolveValidToken(anyString())).thenReturn(foreignToken);

		FleetovoException ex = assertThrows(FleetovoException.class,
				() -> service.verifyPickupOtp("raw-token", new PickupOtpVerifyRequest("123456")));

		assertEquals(ErrorCode.DUTY_NOT_FOUND, ex.getErrorCode());
	}
}

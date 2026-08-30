package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.core.dtos.driverduty.CloseDutyConfirmationResponse;
import com.core.dtos.driverduty.DriverDutyReturnGarageRequest;
import com.core.dtos.driverduty.GarageReturnConfirmationResponse;
import com.core.dtos.driverduty.PickupOtpGenerateResponse;
import com.core.dtos.driverduty.PickupOtpVerifyRequest;
import com.core.dtos.driverduty.PickupOtpVerifyResponse;
import com.core.exception.BusinessException;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutyCheckpoint;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DutyStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.BookingRepository;
import com.core.repositories.DriverDutyAccessTokenRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverDutyExpenseRepository;
import com.core.repositories.DriverDutyLiveLocationRepository;
import com.core.services.common.FileService;
import com.core.services.common.SMSService;
import com.core.ws.DutyLocationChannelRegistry;

/*
 * Covers the four Phase 1 driver-duty-lifecycle additions on
 * ExternalDriverDutyService: real pickup OTP generation/verification, and
 * the return-garage/close-duty confirmations. Uses a real BCryptPasswordEncoder
 * (not mocked) so the OTP hash/verify round-trip is genuinely exercised, not
 * just stubbed to "always pass".
 */
class ExternalDriverDutyServicePhase1Test {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String ENTRY_ID = "entry-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String RAW_TOKEN = "raw-token-value";
	private static final String CUSTOMER_PHONE = "+919876543210";

	private BookingEntryRepository bookingEntryRepository;
	private DriverDutyCheckpointRepository checkpointRepository;
	private SMSService smsService;
	private DriverDutyTokenValidator tokenValidator;
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		checkpointRepository = mock(DriverDutyCheckpointRepository.class);
		smsService = mock(SMSService.class);
		tokenValidator = mock(DriverDutyTokenValidator.class);

		service = new ExternalDriverDutyService(
				bookingEntryRepository,
				mock(BookingRepository.class),
				mock(DriverDutyAccessTokenRepository.class),
				checkpointRepository,
				mock(DriverDutyExpenseRepository.class),
				mock(FileService.class),
				mock(BookingService.class),
				mock(RazorpayPaymentService.class),
				mock(MockPaymentService.class),
				mock(PaymentGatewayConfigService.class),
				tokenValidator,
				mock(ApplicationEventPublisher.class),
				mock(DriverDutyLiveLocationRepository.class),
				mock(DutyLocationChannelRegistry.class),
				mock(FraudSignalService.class),
				new BCryptPasswordEncoder(),
				smsService,
				mock(LocationService.class),
				mock(ObjectProvider.class)
		);

		when(smsService.sendOtp(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
	}

	private BookingEntry entry(DutyStatus status) {
		Client client = new Client();
		client.setPhone(CUSTOMER_PHONE);

		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setClient(client);

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

	// ---------------------------------------------------------------
	// PICKUP OTP
	// ---------------------------------------------------------------

	@Test
	void generatePickupOtp_sendsRealOtp_whenDutyRunning() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token(entry));

		PickupOtpGenerateResponse response = service.generatePickupOtp(RAW_TOKEN);

		assertEquals(true, response.sent());
		assertEquals(false, response.alreadyVerified());
		verify(smsService, times(1)).sendOtp(eq(ORG_ID), eq(CUSTOMER_PHONE), anyString(), anyString());
		assertEquals(true, entry.getPickupOtpHash() != null);
	}

	@Test
	void generatePickupOtp_rejectsWhenDutyNotRunning() {
		BookingEntry entry = entry(DutyStatus.ALLOTTED);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token(entry));

		assertThrows(BusinessException.class, () -> service.generatePickupOtp(RAW_TOKEN));
	}

	@Test
	void generatePickupOtp_isIdempotent_whenAlreadyVerified() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		entry.setPickupOtpVerifiedAt(Instant.now());
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token(entry));

		PickupOtpGenerateResponse response = service.generatePickupOtp(RAW_TOKEN);

		assertEquals(true, response.alreadyVerified());
		verify(smsService, times(0)).sendOtp(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void verifyPickupOtp_succeeds_withCorrectCode() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token(entry));
		service.generatePickupOtp(RAW_TOKEN);

		ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
		verify(smsService).sendOtp(eq(ORG_ID), eq(CUSTOMER_PHONE), otpCaptor.capture(), anyString());
		String realOtp = otpCaptor.getValue();

		PickupOtpVerifyResponse response = service.verifyPickupOtp(RAW_TOKEN, new PickupOtpVerifyRequest(realOtp));

		assertEquals(true, response.verified());
	}

	@Test
	void verifyPickupOtp_rejectsIncorrectCode() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token(entry));
		service.generatePickupOtp(RAW_TOKEN);

		assertThrows(BusinessException.class,
				() -> service.verifyPickupOtp(RAW_TOKEN, new PickupOtpVerifyRequest("000000")));
		assertEquals(1, entry.getPickupOtpAttempts());
	}

	@Test
	void verifyPickupOtp_rejectsWhenNeverGenerated() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token(entry));

		assertThrows(BusinessException.class,
				() -> service.verifyPickupOtp(RAW_TOKEN, new PickupOtpVerifyRequest("123456")));
	}

	@Test
	void verifyPickupOtp_locksOutAfterMaxAttempts() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		stubLockable(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token(entry));
		service.generatePickupOtp(RAW_TOKEN);

		for (int i = 0; i < 4; i++) {
			assertThrows(BusinessException.class,
					() -> service.verifyPickupOtp(RAW_TOKEN, new PickupOtpVerifyRequest("000000")));
		}

		// 5th wrong attempt should trip the max-attempts lockout specifically
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.verifyPickupOtp(RAW_TOKEN, new PickupOtpVerifyRequest("000000")));
		assertEquals(com.core.exception.ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED, ex.getErrorCode());
	}

	// ---------------------------------------------------------------
	// RETURN TO GARAGE / CLOSE DUTY
	// ---------------------------------------------------------------

	@Test
	void confirmGarageReturn_succeeds_whenDutyCompleted() {
		BookingEntry entry = entry(DutyStatus.COMPLETED);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(checkpointRepository.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(Optional.empty());

		GarageReturnConfirmationResponse response = service.confirmGarageReturn(
				RAW_TOKEN, new DriverDutyReturnGarageRequest(null, null, null), "127.0.0.1", "test-agent");

		assertEquals(true, response.confirmed());
	}

	@Test
	void confirmGarageReturn_rejectsBeforeDutyCompleted() {
		BookingEntry entry = entry(DutyStatus.RUNNING);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));

		assertThrows(BusinessException.class,
				() -> service.confirmGarageReturn(RAW_TOKEN, null, "127.0.0.1", "test-agent"));
	}

	@Test
	void confirmGarageReturn_isIdempotent_onDuplicateConfirmation() {
		BookingEntry entry = entry(DutyStatus.COMPLETED);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));

		DriverDutyCheckpoint existing = new DriverDutyCheckpoint();
		existing.setSubmittedAt(Instant.now().minusSeconds(120));
		when(checkpointRepository.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(Optional.of(existing));

		GarageReturnConfirmationResponse response = service.confirmGarageReturn(RAW_TOKEN, null, "127.0.0.1", "test-agent");

		assertEquals(existing.getSubmittedAt(), response.confirmedAt());
	}

	@Test
	void closeDuty_rejectsBeforeGarageReturnConfirmed() {
		BookingEntry entry = entry(DutyStatus.COMPLETED);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(false);

		assertThrows(BusinessException.class,
				() -> service.closeDutyFromDriverApp(RAW_TOKEN, "127.0.0.1", "test-agent"));
	}

	@Test
	void closeDuty_succeeds_afterGarageReturnConfirmed() {
		BookingEntry entry = entry(DutyStatus.COMPLETED);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(true);
		when(checkpointRepository.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(ENTRY_ID, DriverDutyCheckpointType.CLOSE))
				.thenReturn(Optional.empty());

		CloseDutyConfirmationResponse response = service.closeDutyFromDriverApp(RAW_TOKEN, "127.0.0.1", "test-agent");

		assertEquals(true, response.closed());
	}

	@Test
	void closeDuty_isIdempotent_onDoubleClose() {
		BookingEntry entry = entry(DutyStatus.COMPLETED);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(true);

		DriverDutyCheckpoint existing = new DriverDutyCheckpoint();
		existing.setSubmittedAt(Instant.now().minusSeconds(60));
		when(checkpointRepository.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(ENTRY_ID, DriverDutyCheckpointType.CLOSE))
				.thenReturn(Optional.of(existing));

		CloseDutyConfirmationResponse response = service.closeDutyFromDriverApp(RAW_TOKEN, "127.0.0.1", "test-agent");

		assertEquals(existing.getSubmittedAt(), response.closedAt());
	}
}

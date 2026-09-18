package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.core.dtos.driverduty.CashPaymentConfirmationResponse;
import com.core.dtos.driverduty.CloseDutyConfirmationResponse;
import com.core.events.PaymentConfirmedEvent;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.QrPaymentStatusResponse;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutyCheckpoint;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentMode;
import com.core.models.enums.PaymentStatus;
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
 * P0 revenue-integrity fix -- replaces the Chauffeur app's mocked cash
 * confirmation with a real, backend-authoritative cash payment
 * (confirmCashPayment) and a duty-completion guard (closeDutyFromDriverApp)
 * that refuses to close a duty with a genuinely outstanding balance,
 * regardless of collection method. Both reuse the existing Payment
 * model/state machine (PaymentMode.CASH, PaymentGateway.MANUAL_ENTRY,
 * PaymentStatus.CONFIRMED) -- no parallel ledger.
 */
class ExternalDriverDutyServiceCashPaymentTest {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String ENTRY_ID = "entry-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String RAW_TOKEN = "raw-token-value";
	private static final String CASH_CONTEXT = "DRIVER_DUTY_CASH";

	private BookingEntryRepository bookingEntryRepository;
	private DriverDutyCheckpointRepository checkpointRepository;
	private PaymentRepository paymentRepo;
	private PaymentEventAssembler paymentEventAssembler;
	private ApplicationEventPublisher eventPublisher;
	private RazorpayPaymentService razorpayPaymentService;
	private MockPaymentService mockPaymentService;
	private DriverDutyTokenValidator tokenValidator;
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		checkpointRepository = mock(DriverDutyCheckpointRepository.class);
		paymentRepo = mock(PaymentRepository.class);
		paymentEventAssembler = mock(PaymentEventAssembler.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		razorpayPaymentService = mock(RazorpayPaymentService.class);
		mockPaymentService = mock(MockPaymentService.class);
		tokenValidator = mock(DriverDutyTokenValidator.class);

		service = new ExternalDriverDutyService(
				bookingEntryRepository,
				mock(BookingRepository.class),
				mock(DriverDutyAccessTokenRepository.class),
				checkpointRepository,
				mock(DriverDutyExpenseRepository.class),
				mock(FileService.class),
				mock(BookingService.class),
				razorpayPaymentService,
				mockPaymentService,
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
				paymentRepo,
				paymentEventAssembler
		);

		when(paymentEventAssembler.toPaymentConfirmedEvent(any(Booking.class), any(Payment.class)))
				.thenReturn(mock(PaymentConfirmedEvent.class));
	}

	private Booking booking(BigDecimal total, BigDecimal alreadyPaid) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setTotal(Money.INR(total));

		Client client = new Client();
		booking.setClient(client);

		if (alreadyPaid != null && alreadyPaid.compareTo(BigDecimal.ZERO) > 0) {
			Payment confirmed = new Payment();
			confirmed.setStatus(PaymentStatus.CONFIRMED);
			confirmed.setReceivedAmount(Money.INR(alreadyPaid));
			booking.setPayments(List.of(confirmed));
		} else {
			booking.setPayments(List.of());
		}

		return booking;
	}

	private BookingEntry entry(DutyStatus status, Booking booking) {
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

	private void stubNoExistingCashPayment() {
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, CASH_CONTEXT, DUTY_ID)).thenReturn(Optional.empty());
	}

	// =====================================================================
	// 1/2. Authorized chauffeur records a valid, server-derived cash payment
	// =====================================================================

	@Test
	void confirmCashPayment_authorizedChauffeur_recordsServerDerivedAmount() {
		Booking booking = booking(new BigDecimal("2000.00"), new BigDecimal("500.00"));
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		stubNoExistingCashPayment();
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
			Payment p = inv.getArgument(0);
			p.setId("payment-cash-1");
			return p;
		});

		CashPaymentConfirmationResponse response = service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent");

		assertTrue(response.confirmed());
		// 2000 total - 500 already confirmed = 1500 outstanding -- never a
		// client-submitted figure, since this endpoint accepts no request body.
		assertEquals(0, new BigDecimal("1500.00").compareTo(response.amount()));

		verify(paymentRepo, times(1)).save(any(Payment.class));
		verify(eventPublisher, times(1)).publishEvent(any(PaymentConfirmedEvent.class));
	}

	@Test
	void confirmCashPayment_savesWithCashModeAndManualEntryGateway_notASecondLedger() {
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		stubNoExistingCashPayment();
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));

		org.mockito.ArgumentCaptor<Payment> captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
		when(paymentRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

		service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent");

		Payment saved = captor.getValue();
		assertEquals(PaymentMode.CASH, saved.getPaymentMode());
		assertEquals(PaymentGateway.MANUAL_ENTRY, saved.getGateway());
		assertEquals(PaymentStatus.CONFIRMED, saved.getStatus());
		assertEquals(DUTY_ID, saved.getCashCollectionReference());
	}

	// =====================================================================
	// 4/5. Duplicate/retry safety (idempotency)
	// =====================================================================

	@Test
	void confirmCashPayment_secondCallForSameDuty_returnsSameResult_neverCreatesASecondPayment() {
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));

		Payment existing = new Payment();
		existing.setId("payment-existing");
		existing.setStatus(PaymentStatus.CONFIRMED);
		existing.setReceivedAmount(Money.INR(new BigDecimal("1000.00")));
		existing.setTransactionDate(Instant.now());

		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, CASH_CONTEXT, DUTY_ID)).thenReturn(Optional.of(existing));

		CashPaymentConfirmationResponse response = service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent");

		assertTrue(response.confirmed());
		assertEquals("payment-existing", response.paymentId());
		verify(paymentRepo, never()).save(any(Payment.class));
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void confirmCashPayment_concurrentRaceLoser_recoversTheWinningRow_insteadOfFailing() {
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));

		Payment winner = new Payment();
		winner.setId("payment-winner");
		winner.setStatus(PaymentStatus.CONFIRMED);
		winner.setReceivedAmount(Money.INR(new BigDecimal("1000.00")));
		winner.setTransactionDate(Instant.now());

		// First lookup (pre-insert existence check): nothing yet. Second
		// lookup (inside the DataIntegrityViolationException recovery path):
		// the concurrent winner's row is now visible.
		when(paymentRepo.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
				ORG_ID, BOOKING_ID, CASH_CONTEXT, DUTY_ID))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(winner));
		when(paymentRepo.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("uk_payment_cash_collection_reference"));

		CashPaymentConfirmationResponse response = service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent");

		assertTrue(response.confirmed());
		assertEquals("payment-winner", response.paymentId());
	}

	// =====================================================================
	// 6. Idempotency is duty-scoped, not global -- a different duty gets its
	//    own independent cash collection.
	// =====================================================================

	@Test
	void confirmCashPayment_differentDuty_isIndependent_ofAnotherDutysCashPayment() {
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		stubNoExistingCashPayment();
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

		service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent");

		// The lookup that governs idempotency is scoped to THIS duty's id --
		// structurally impossible to collide with a different dutyId's row.
		verify(paymentRepo, times(1))
				.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						eq(ORG_ID), eq(BOOKING_ID), eq(CASH_CONTEXT), eq(DUTY_ID));
	}

	// =====================================================================
	// 7/8. Cannot record cash for another chauffeur's duty / another org
	// =====================================================================

	@Test
	void confirmCashPayment_onlyEverTargetsTheTokensOwnDutyAndOrg() {
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		stubNoExistingCashPayment();
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

		service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent");

		// There is no dutyId/orgId parameter on this endpoint at all -- both
		// come exclusively from the resolved access token, so a chauffeur
		// can never point this call at a different duty or org than the one
		// their own token was minted for.
		verify(bookingEntryRepository, times(1)).findByDutyIdAndOrgId(DUTY_ID, ORG_ID);
		verify(bookingEntryRepository, never()).findByDutyIdAndOrgId(eq(DUTY_ID), org.mockito.ArgumentMatchers.argThat(o -> !ORG_ID.equals(o)));
	}

	@Test
	void confirmCashPayment_dutyNotFoundInTokensOrg_isRejected() {
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.empty());
		BookingEntry phantom = entry(DutyStatus.COMPLETED, booking(BigDecimal.TEN, null));
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(phantom));

		assertThrows(RuntimeException.class,
				() -> service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent"));

		verify(paymentRepo, never()).save(any(Payment.class));
	}

	// =====================================================================
	// 9. Invalid/missing authentication (token) is rejected
	// =====================================================================

	@Test
	void confirmCashPayment_invalidToken_isRejected_beforeAnyPaymentWrite() {
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN))
				.thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "Invalid duty link"));

		assertThrows(BusinessException.class,
				() -> service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent"));

		verify(paymentRepo, never()).save(any(Payment.class));
	}

	@Test
	void confirmCashPayment_dutyNotYetCompleted_isRejected() {
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.RUNNING, booking);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));

		assertThrows(BusinessException.class,
				() -> service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent"));

		verify(paymentRepo, never()).save(any(Payment.class));
	}

	// =====================================================================
	// 10. Prepaid/fully-settled booking is never charged cash
	// =====================================================================

	@Test
	void confirmCashPayment_bookingAlreadyFullyPaid_isRejected_notChargedAgain() {
		Booking booking = booking(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		stubNoExistingCashPayment();
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.confirmCashPayment(RAW_TOKEN, "127.0.0.1", "test-agent"));

		assertEquals(ErrorCode.PAYMENT_ALREADY_CONFIRMED, ex.getErrorCode());
		verify(paymentRepo, never()).save(any(Payment.class));
	}

	// =====================================================================
	// 11/12/13. Duty-completion guard
	// =====================================================================

	@Test
	void closeDuty_succeeds_afterCashPaymentSettlesTheBalance() {
		Booking booking = booking(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(true);
		when(checkpointRepository.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(ENTRY_ID, DriverDutyCheckpointType.CLOSE))
				.thenReturn(Optional.empty());

		CloseDutyConfirmationResponse response = service.closeDutyFromDriverApp(RAW_TOKEN, "127.0.0.1", "test-agent");

		assertTrue(response.closed());
	}

	@Test
	void closeDuty_rejectsWhenOutstandingCashPaymentIsMissing_doesNotPartiallyClose() {
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(true);
		when(checkpointRepository.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(ENTRY_ID, DriverDutyCheckpointType.CLOSE))
				.thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.closeDutyFromDriverApp(RAW_TOKEN, "127.0.0.1", "test-agent"));

		assertEquals(ErrorCode.PAYMENT_REQUIRED, ex.getErrorCode());
		// No implicit/fake payment and no checkpoint written -- a rejected
		// close must not leave any partial trace.
		verify(checkpointRepository, never()).save(
				org.mockito.ArgumentMatchers.argThat(c -> c.getCheckpointType() == DriverDutyCheckpointType.CLOSE));
		verify(paymentRepo, never()).save(any(Payment.class));
	}

	@Test
	void closeDuty_cannotBeUsedToBypassCashPayment_evenWithGarageReturnConfirmed() {
		// Same shape as the rejection test above, phrased against the exact
		// wording of the requirement: garage-return being confirmed is not
		// sufficient on its own to close a duty with money still owed.
		Booking booking = booking(new BigDecimal("500.00"), new BigDecimal("100.00"));
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(true);
		when(checkpointRepository.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(ENTRY_ID, DriverDutyCheckpointType.CLOSE))
				.thenReturn(Optional.empty());

		assertThrows(BusinessException.class,
				() -> service.closeDutyFromDriverApp(RAW_TOKEN, "127.0.0.1", "test-agent"));
	}

	// =====================================================================
	// 14. Repeated duty completion remains idempotent
	// =====================================================================

	@Test
	void closeDuty_alreadyClosed_staysIdempotent_regardlessOfCurrentPaymentState() {
		// A duty that was validly closed once (CLOSE checkpoint already
		// exists) must keep succeeding on replay even if this particular
		// mock setup's booking shows an outstanding balance -- the
		// idempotency short-circuit must run BEFORE the payment guard, not
		// re-litigate a close that already genuinely happened.
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		stubLockable(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(token(entry));
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(ENTRY_ID, DriverDutyCheckpointType.GARAGE_RETURN))
				.thenReturn(true);

		DriverDutyCheckpoint existingClose = new DriverDutyCheckpoint();
		existingClose.setSubmittedAt(Instant.now().minusSeconds(60));
		when(checkpointRepository.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(ENTRY_ID, DriverDutyCheckpointType.CLOSE))
				.thenReturn(Optional.of(existingClose));

		CloseDutyConfirmationResponse response = service.closeDutyFromDriverApp(RAW_TOKEN, "127.0.0.1", "test-agent");

		assertEquals(existingClose.getSubmittedAt(), response.closedAt());
	}

	// =====================================================================
	// 15/16. checkQrPaymentStatus: booking-level settlement / QR path intact
	// =====================================================================

	@Test
	void checkQrPaymentStatus_reportsPaid_whenCashAlreadySettledTheBooking_recoveryAfterRestart() throws Exception {
		Booking booking = booking(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		DriverDutyAccessToken accessToken = token(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(accessToken);

		QrPaymentStatusResponse response = service.checkQrPaymentStatus(RAW_TOKEN);

		assertTrue(response.isPaid());
		assertEquals("PAID", response.getStatus());
		// Backend state alone drove this -- never even asked the gateway,
		// digital or mock, whose payment method actually settled it.
		verify(razorpayPaymentService, never()).isPaidByQR(anyString(), anyString(), anyString());
		verify(mockPaymentService, never()).mockQrStatus(anyString(), anyString(), anyString());
	}

	@Test
	void checkQrPaymentStatus_stillDelegatesToQrGateway_whenGenuinelyOutstanding() throws Exception {
		Booking booking = booking(new BigDecimal("1000.00"), null);
		BookingEntry entry = entry(DutyStatus.COMPLETED, booking);
		DriverDutyAccessToken accessToken = token(entry);
		when(tokenValidator.resolveTokenForPaymentStatus(RAW_TOKEN)).thenReturn(accessToken);
		// paymentGatewayConfigService is an unstubbed mock -- Mockito's default
		// answer for an Optional-returning method is Optional.empty(), so
		// resolveEffectiveGateway falls through to its own RAZORPAY default,
		// exercising the real (non-mock) gateway branch below.
		when(razorpayPaymentService.isPaidByQR(ORG_ID, BOOKING_ID, DUTY_ID)).thenReturn(
				QrPaymentStatusResponse.builder().status("PENDING").paid(false).amount(BigDecimal.TEN).build());

		QrPaymentStatusResponse response = service.checkQrPaymentStatus(RAW_TOKEN);

		assertEquals(false, response.isPaid());
		verify(razorpayPaymentService, times(1)).isPaidByQR(ORG_ID, BOOKING_ID, DUTY_ID);
	}
}

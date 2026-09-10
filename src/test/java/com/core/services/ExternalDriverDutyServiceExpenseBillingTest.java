package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.core.dtos.driverduty.DriverDutyEndRequest;
import com.core.dtos.driverduty.DriverDutyExpenseInput;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.location.api.DistanceTimeResult;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutyExpense;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DriverDutyExpenseType;
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
 * Chauffeur "Final Fare + Expense" production task -- covers the two real
 * gaps found while tracing driver-submitted duty expenses
 * (TOLL/PARKING/STATE_TAX/OTHER, see DriverDutyExpense) into the
 * customer-billable total:
 *
 * 1. A non-positive amount used to be silently skipped (`continue`), so an
 *    expense the driver believed was recorded could vanish from the bill
 *    with no error and no trace -- now the whole submission fails loudly
 *    instead, exactly like every other malformed duty-end field.
 * 2. Every valid, positive-amount expense (any of the 4 real categories)
 *    is added to entry.getCharges() and therefore to dutyTotal via
 *    BookingUtil.calculateFinalTotal -- proven here directly against the
 *    real completion service, not a mocked pricing stub.
 *
 * Same constructor-injection style as ExternalDriverDutyServiceGarageReturnBillingTest
 * (this class's sibling for the garage-return-distance side of the same bill).
 */
class ExternalDriverDutyServiceExpenseBillingTest {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String ENTRY_ID = "entry-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String CUSTOMER_PHONE = "+919876543210";

	private static final AddressSnapshot GARAGE = new AddressSnapshot("Garage", null, 12.9716, 77.5946);
	private static final AddressSnapshot DROP = new AddressSnapshot("Drop", null, 13.0500, 77.6200);

	private BookingEntryRepository bookingEntryRepository;
	private DriverDutyCheckpointRepository checkpointRepository;
	private DriverDutyExpenseRepository expenseRepository;
	private LocationService locationService;
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		checkpointRepository = mock(DriverDutyCheckpointRepository.class);
		expenseRepository = mock(DriverDutyExpenseRepository.class);
		locationService = mock(LocationService.class);

		service = new ExternalDriverDutyService(
				bookingEntryRepository,
				mock(BookingRepository.class),
				mock(DriverDutyAccessTokenRepository.class),
				checkpointRepository,
				expenseRepository,
				mock(FileService.class),
				mock(BookingService.class),
				mock(RazorpayPaymentService.class),
				mock(MockPaymentService.class),
				mock(PaymentGatewayConfigService.class),
				mock(DriverDutyTokenValidator.class),
				mock(ApplicationEventPublisher.class),
				mock(DriverDutyLiveLocationRepository.class),
				mock(DutyLocationChannelRegistry.class),
				mock(FraudSignalService.class),
				new BCryptPasswordEncoder(),
				mock(SMSService.class),
				locationService,
				mock(ObjectProvider.class),
				mock(PaymentRepository.class),
				mock(PaymentEventAssembler.class)
		);
	}

	// distance=60km/day, extraPerKM=50, extraPerHS=0 -- odometer-only distance
	// (150-100=50km, see endRequest()) stays under the allowance, so any
	// nonzero dutyTotal beyond the 1000 base fare is attributable only to the
	// expenses under test, never to distance/time.
	private PackageSnapshot dayPackage() {
		PackageSnapshot pack = new PackageSnapshot();
		pack.setDistance(60);
		pack.setTime(0);
		pack.setUnit("DAY");
		pack.setBaseFare(Money.INR(BigDecimal.valueOf(1000)));
		pack.setExtraPerKM(Money.INR(BigDecimal.valueOf(50)));
		pack.setExtraPerHS(Money.INR(BigDecimal.ZERO));
		pack.setNightCharge(Money.INR(BigDecimal.ZERO));
		return pack;
	}

	private BookingEntry entry() {
		Client client = new Client();
		client.setPhone(CUSTOMER_PHONE);

		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setClient(client);
		booking.setStatus(BookingStatus.CONFIRMED);

		BookingEntry e = new BookingEntry();
		e.setId(ENTRY_ID);
		e.setDutyId(DUTY_ID);
		e.setBooking(booking);
		e.setStatus(DutyStatus.RUNNING);
		e.setPack(dayPackage());
		e.setStartAt(Instant.now().minusSeconds(3600));
		e.setStartingKM(100);
		e.setPickupOtpVerifiedAt(Instant.now().minusSeconds(1800));
		e.setGarageLocation(GARAGE);
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
		when(checkpointRepository.existsByBookingEntry_IdAndCheckpointType(eq(ENTRY_ID), any())).thenReturn(false);
		// No garage-return distance in play for these tests -- isolates the
		// dutyTotal delta to the expenses under test (same technique the
		// non-positive-amount test below relies on for its assertion).
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(0.0, 0, false, "OPEN_ROUTE_SERVICE", null));
	}

	// odometer reading alone = 150 - 100 = 50km, under the 60km allowance.
	private DriverDutyEndRequest endRequest(List<DriverDutyExpenseInput> extraCharges) {
		return new DriverDutyEndRequest(150, null, null, Instant.now(), null, extraCharges);
	}

	@Test
	void singleTollExpense_isPersistedAndAddedToTheBill() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);

		DriverDutyExpenseInput toll = new DriverDutyExpenseInput(DriverDutyExpenseType.TOLL, BigDecimal.valueOf(120), "NH48 toll");

		// completeDutyEntryAndFinalizeBooking's return type (DutyCompletionResult)
		// is a private nested record -- same reason
		// ExternalDriverDutyServiceGarageReturnBillingTest never captures it
		// either -- so this test (like that one) asserts against `entry`'s own
		// mutated, public-getter state instead.
		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(List.of(toll)), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		// base fare 1000 + 120 toll = 1120, no distance/time overage.
		assertEquals(new BigDecimal("1120.00"), entry.getDutyTotal().getAmount());

		ArgumentCaptor<DriverDutyExpense> captor = ArgumentCaptor.forClass(DriverDutyExpense.class);
		verify(expenseRepository).save(captor.capture());
		DriverDutyExpense saved = captor.getValue();
		assertEquals(ORG_ID, saved.getOrgId());
		assertEquals(DUTY_ID, saved.getDutyId());
		assertEquals(DriverDutyExpenseType.TOLL, saved.getExpenseType());
		assertEquals(new BigDecimal("120.00"), saved.getAmount().getAmount());
		assertEquals("NH48 toll", saved.getDescription());
		// DRIVER_SUBMITTED is the only status this path ever sets -- there is
		// no approval step anywhere in the codebase that transitions it (see
		// this task's final report) -- documented here as current, real
		// behavior, not asserted as "correct".
		assertEquals(com.core.models.enums.DriverDutyExpenseStatus.DRIVER_SUBMITTED, saved.getStatus());
	}

	@Test
	void multipleExpenseCategories_allSumIntoTheBill() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);

		List<DriverDutyExpenseInput> expenses = List.of(
				new DriverDutyExpenseInput(DriverDutyExpenseType.TOLL, BigDecimal.valueOf(120), "Toll"),
				new DriverDutyExpenseInput(DriverDutyExpenseType.PARKING, BigDecimal.valueOf(50), "Mall parking"),
				new DriverDutyExpenseInput(DriverDutyExpenseType.STATE_TAX, BigDecimal.valueOf(300), "Inter-state permit"),
				new DriverDutyExpenseInput(DriverDutyExpenseType.OTHER, BigDecimal.valueOf(75), "Misc")
		);

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(expenses), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		// base 1000 + 120 + 50 + 300 + 75 = 1545.
		assertEquals(new BigDecimal("1545.00"), entry.getDutyTotal().getAmount());
		verify(expenseRepository, times(4)).save(any(DriverDutyExpense.class));
	}

	@Test
	void zeroAmountExpense_isRejectedLoudly_notSilentlyDropped() {
		BookingEntry entry = entry();
		stubLockable(entry);

		DriverDutyExpenseInput invalid = new DriverDutyExpenseInput(DriverDutyExpenseType.OTHER, BigDecimal.ZERO, "Typo");

		BusinessException ex = assertThrows(BusinessException.class, () -> service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(List.of(invalid)), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent"));

		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		// Nothing partially applied -- entry never transitioned, no expense row written.
		verify(expenseRepository, times(0)).save(any(DriverDutyExpense.class));
	}

	@Test
	void negativeAmountExpense_isRejectedLoudly() {
		BookingEntry entry = entry();
		stubLockable(entry);

		DriverDutyExpenseInput invalid = new DriverDutyExpenseInput(DriverDutyExpenseType.TOLL, BigDecimal.valueOf(-50), null);

		assertThrows(BusinessException.class, () -> service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(List.of(invalid)), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent"));
	}

	@Test
	void aValidExpenseBeforeAnInvalidOne_isNotPartiallyBilled() throws Exception {
		// Proves the P0 fix is atomic, not "stop at the bad one but keep what
		// already ran" -- completeDutyEntryAndFinalizeBooking is @Transactional,
		// so a mid-loop throw must roll back the whole unit of work, including
		// entry mutations already made by earlier iterations. This test can only
		// prove the in-memory state stays uncommitted from Java's own point of
		// view (no real transaction manager runs against a mock persistence
		// layer) -- verified precisely: dutyTotal is never (re)computed to
		// include the valid expense once the loop throws, because
		// BookingUtil.calculateTotal only runs after the whole loop completes.
		BookingEntry entry = entry();
		stubLockable(entry);

		List<DriverDutyExpenseInput> expenses = List.of(
				new DriverDutyExpenseInput(DriverDutyExpenseType.TOLL, BigDecimal.valueOf(120), "Toll"),
				new DriverDutyExpenseInput(DriverDutyExpenseType.OTHER, BigDecimal.ZERO, "Bad")
		);

		assertThrows(BusinessException.class, () -> service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(expenses), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent"));

		// dutyTotal was never set by this call at all (calculateTotal runs after
		// the loop, which never completed).
		assertNull(entry.getDutyTotal());
	}

	@Test
	void receiptPhoto_isPersistedAgainstItsOwnExpense_positionallyMatched() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);

		FileService fileService = mock(FileService.class);
		when(fileService.saveFile(any())).thenReturn("receipt-1.jpg");

		ExternalDriverDutyService serviceWithFiles = new ExternalDriverDutyService(
				bookingEntryRepository, mock(BookingRepository.class), mock(DriverDutyAccessTokenRepository.class),
				checkpointRepository, expenseRepository, fileService, mock(BookingService.class),
				mock(RazorpayPaymentService.class), mock(MockPaymentService.class), mock(PaymentGatewayConfigService.class),
				mock(DriverDutyTokenValidator.class), mock(ApplicationEventPublisher.class),
				mock(DriverDutyLiveLocationRepository.class), mock(DutyLocationChannelRegistry.class),
				mock(FraudSignalService.class), new BCryptPasswordEncoder(), mock(SMSService.class),
				locationService, mock(ObjectProvider.class), mock(PaymentRepository.class), mock(PaymentEventAssembler.class));

		DriverDutyExpenseInput toll = new DriverDutyExpenseInput(DriverDutyExpenseType.TOLL, BigDecimal.valueOf(120), "Toll");
		MockMultipartFile receipt = new MockMultipartFile("receiptPhotos", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

		serviceWithFiles.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(List.of(toll)), DROP, "odometer.jpg", List.of(receipt), "127.0.0.1", "test-agent");

		ArgumentCaptor<DriverDutyExpense> captor = ArgumentCaptor.forClass(DriverDutyExpense.class);
		verify(expenseRepository).save(captor.capture());
		assertEquals("receipt-1.jpg", captor.getValue().getReceiptPhoto());
	}

	@Test
	void noExpenses_leavesTheBillAtExactlyTheBaseFare() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(null), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		assertEquals(new BigDecimal("1000.00"), entry.getDutyTotal().getAmount());
		verify(expenseRepository, times(0)).save(any());
	}
}

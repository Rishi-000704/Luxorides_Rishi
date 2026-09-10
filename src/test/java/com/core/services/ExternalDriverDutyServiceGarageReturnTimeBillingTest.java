package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.core.dtos.driverduty.DriverDutyEndRequest;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.location.api.DistanceTimeResult;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.DriverDutyAccessToken;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.Money;
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
 * Companion to ExternalDriverDutyServiceGarageReturnBillingTest, which
 * proves the C->A route's DISTANCE feeds the bill (closingKM). This class
 * proves the same route's DURATION feeds the bill too: completeDutyEntryAndFinalizeBooking
 * sets entry.setEndAt(actualDropSubmittedAt.plusSeconds(garageReturn.durationSeconds()))
 * BEFORE calling BookingUtil.calculateTotal, so runningMinutes (the value the
 * hourly-package extra-time rule actually reads) already represents the
 * complete projected A(garage)->B(pickup)->C(drop)->A(garage) trip time, not
 * just the served A->B->C portion -- symmetric with how closingKM already
 * represents the complete A->A distance. Also proves this comes from a
 * SINGLE routing-provider call reused for both distance and duration (no
 * separate "billing" call), and that the addition happens exactly once
 * (no double-counting of the return duration).
 */
class ExternalDriverDutyServiceGarageReturnTimeBillingTest {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String ENTRY_ID = "entry-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String CUSTOMER_PHONE = "+919876543210";

	private static final AddressSnapshot GARAGE = new AddressSnapshot("Garage", null, 12.9716, 77.5946);
	private static final AddressSnapshot DROP = new AddressSnapshot("Drop", null, 13.0500, 77.6200);

	private BookingEntryRepository bookingEntryRepository;
	private DriverDutyCheckpointRepository checkpointRepository;
	private LocationService locationService;
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		checkpointRepository = mock(DriverDutyCheckpointRepository.class);
		locationService = mock(LocationService.class);

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

	// time=4h included, distance=100km included (deliberately generous so
	// these time-focused tests don't accidentally also trip extra distance);
	// extraPerHS=100/hr so a 0.5hr quarter-hour charge is an easy-to-verify 50.
	private PackageSnapshot hourlyPackage() {
		PackageSnapshot pack = new PackageSnapshot();
		pack.setDistance(100);
		pack.setTime(4);
		pack.setUnit("HOURLY");
		pack.setBaseFare(Money.INR(BigDecimal.valueOf(1200)));
		pack.setExtraPerKM(Money.INR(BigDecimal.valueOf(20)));
		pack.setExtraPerHS(Money.INR(BigDecimal.valueOf(100)));
		pack.setNightCharge(Money.INR(BigDecimal.ZERO));
		return pack;
	}

	private BookingEntry entry(long actualServedMinutes) {
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
		e.setPack(hourlyPackage());
		// The actual A->B->C elapsed time is startAt -> "now" (submitEnd
		// stamps actualDropSubmittedAt = Instant.now() internally), so
		// backdating startAt by the intended served minutes controls it.
		e.setStartAt(Instant.now().minusSeconds(actualServedMinutes * 60));
		e.setStartingKM(1000);
		e.setPickupOtpVerifiedAt(Instant.now().minusSeconds((actualServedMinutes - 10) * 60));
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
	}

	// Odometer delta = 5km (1005-1000), nowhere near the 100km allowance --
	// isolates these tests to the time dimension only.
	private DriverDutyEndRequest endRequest() {
		return new DriverDutyEndRequest(1005, null, null, Instant.now(), null, null);
	}

	/*
	 * Phase 18 "Test 3": actual A->B->C = 3h40m, real routed C->A = 50min.
	 * Complete A->A = 4h30m against a 4h package -> 30 extra minutes ->
	 * quarter-hour billing rounds 30min to exactly 0.5hr -> +50 on top of
	 * the flat 1200 base fare. If the C->A duration were only shown as an
	 * ETA and never fed into runningMinutes, this would incorrectly settle
	 * at extraChargeableTime=0 / dutyTotal=1200.00.
	 */
	@Test
	void returnLegDuration_pushesHourlyDutyOverAllowance_createsExtraTimeCharge() throws Exception {
		BookingEntry entry = entry(220); // 3h40m actual A->B->C
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(5.0, 3000, false, "OPEN_ROUTE_SERVICE", null)); // 50min

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		assertEquals(270L, Duration.between(entry.getStartAt(), entry.getEndAt()).toMinutes(),
				"complete A->A time should be 3h40m actual + 50min routed return = 4h30m");
		assertEquals(0.5f, entry.getExtraChargebleTime());
		assertEquals(new BigDecimal("1250.00"), entry.getDutyTotal().getAmount());
	}

	/*
	 * Phase 18 "Test 4": actual A->B->C = 2h30m, real routed C->A = 45min.
	 * Complete A->A = 3h15m, still under the 4h package -> no extra time.
	 * Proves the return leg is genuinely evaluated against the package
	 * allowance, not unconditionally billed.
	 */
	@Test
	void returnLegDuration_stayingWithinAllowance_createsNoExtraTimeCharge() throws Exception {
		BookingEntry entry = entry(150); // 2h30m actual A->B->C
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(5.0, 2700, false, "OPEN_ROUTE_SERVICE", null)); // 45min

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		assertEquals(195L, Duration.between(entry.getStartAt(), entry.getEndAt()).toMinutes());
		assertEquals(0f, entry.getExtraChargebleTime());
		assertEquals(new BigDecimal("1200.00"), entry.getDutyTotal().getAmount());
	}

	/*
	 * Phase 18 "Test 5" (time half) combined with an odometer reading big
	 * enough to also cross the distance allowance -- proves extra distance
	 * and extra time are both computed, from the SAME completion call,
	 * without either one suppressing or double-charging the other.
	 */
	@Test
	void returnLeg_exceedingBothAllowances_billsExtraDistanceAndExtraTimeTogetherExactlyOnce() throws Exception {
		BookingEntry entry = entry(230); // 3h50m actual A->B->C
		entry.setStartingKM(1000);
		stubLockable(entry);
		// 12km / 45min routed return.
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(12.0, 2700, false, "OPEN_ROUTE_SERVICE", null));

		// Odometer delta alone = 110km (1110-1000), so closingKM = 1110+12=1122,
		// runningDistance = 122km, 22km over the 100km allowance.
		DriverDutyEndRequest request = new DriverDutyEndRequest(1110, null, null, Instant.now(), null, null);

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), request, DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		// Time: 230 + 45 = 275min = 4h35min, 35min over the 4h allowance ->
		// quarter-hour billing rounds 35min up to 0.75hr.
		assertEquals(275L, Duration.between(entry.getStartAt(), entry.getEndAt()).toMinutes());
		assertEquals(22, entry.getExtraChargebleDistance());
		assertEquals(0.75f, entry.getExtraChargebleTime());

		// 1200 base + 22km*20 (=440) + 0.75hr*100 (=75) = 1715.00, each
		// component contributing exactly once.
		assertEquals(new BigDecimal("1715.00"), entry.getDutyTotal().getAmount());
	}

	/*
	 * Phase 18 "Test 7": the C->A route is computed ONCE per completion and
	 * that single result is reused for both the distance and the duration
	 * side of the bill -- never a separate provider call for "billing
	 * distance" vs "billing duration".
	 */
	@Test
	void oneRoutingCall_feedsBothDistanceAndDurationOfTheBill_noDoubleRouting() throws Exception {
		BookingEntry entry = entry(220);
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(5.0, 3000, false, "OPEN_ROUTE_SERVICE", null));

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		verify(locationService, times(1)).calculateDistanceAndTime(DROP, GARAGE);
		// Both sides of the bill moved off the same single result: closingKM
		// reflects the 5km, and endAt reflects the 3000s, from that one call.
		assertEquals(1010, entry.getClosingKM()); // 1005 + ceil(5.0)
		assertEquals(270L, Duration.between(entry.getStartAt(), entry.getEndAt()).toMinutes());
	}

	/*
	 * A stale/estimated C->A route must not silently inflate the customer's
	 * billed TIME either -- mirrors the existing distance-side safety guard
	 * (ExternalDriverDutyServiceGarageReturnBillingTest.staleCacheFallbackRoute_...).
	 * GarageReturnEstimate.zero() has durationSeconds=0, so entry.getEndAt()
	 * falls back to exactly the real drop timestamp with no added minutes.
	 */
	@Test
	void staleOrEstimatedReturnRoute_addsNoDurationToTheBilledTime() throws Exception {
		BookingEntry entry = entry(220);
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(5.0, 3000, true, "OPEN_ROUTE_SERVICE", null)); // estimated=true

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		// Complete time falls back to exactly the served 220 minutes -- the
		// unreliable 50min return estimate is excluded, not silently added.
		assertEquals(220L, Duration.between(entry.getStartAt(), entry.getEndAt()).toMinutes());
		assertEquals(0f, entry.getExtraChargebleTime());
		assertEquals(new BigDecimal("1200.00"), entry.getDutyTotal().getAmount());
	}

	/*
	 * Phase 18 "Test 6", applied to the actual BILLING call path (not just
	 * the read-only /route/{leg} endpoint): capture the exact coordinates
	 * handed to the routing provider at completion time and assert it is
	 * genuinely Drop(C) -> Garage(A), never Garage->Drop (reversed) or some
	 * other leg's pair. GARAGE and DROP are deliberately non-symmetric
	 * (different lat AND lon deltas) so a swapped call is distinguishable
	 * from the correct one by more than sign alone.
	 */
	@Test
	void returnRoute_isCalledDropToGarage_neverGarageToDrop_usingCapturedArguments() throws Exception {
		BookingEntry entry = entry(220);
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(any(), any()))
				.thenReturn(new DistanceTimeResult(5.0, 3000, false, "OPEN_ROUTE_SERVICE", null));

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		ArgumentCaptor<AddressSnapshot> fromCaptor = ArgumentCaptor.forClass(AddressSnapshot.class);
		ArgumentCaptor<AddressSnapshot> toCaptor = ArgumentCaptor.forClass(AddressSnapshot.class);
		verify(locationService, times(1)).calculateDistanceAndTime(fromCaptor.capture(), toCaptor.capture());

		assertEquals(DROP.getLatitude(), fromCaptor.getValue().getLatitude());
		assertEquals(DROP.getLongitude(), fromCaptor.getValue().getLongitude());
		assertEquals(GARAGE.getLatitude(), toCaptor.getValue().getLatitude());
		assertEquals(GARAGE.getLongitude(), toCaptor.getValue().getLongitude());

		// Explicitly rule out the reversed call: "from" must not be the
		// garage coordinates (which is what a Garage->Drop bug would pass).
		assertNotEquals(GARAGE.getLatitude(), fromCaptor.getValue().getLatitude());
	}
}

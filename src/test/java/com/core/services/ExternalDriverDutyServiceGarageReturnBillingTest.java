package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
 * P0 task 6 -- calculateGarageReturnEstimate's route distance feeds directly
 * into closingKM (billableClosingKm in completeDutyEntryAndFinalizeBooking)
 * and from there into BookingUtil.calculateTotal's extraChargeableDistance,
 * i.e. it can directly create or inflate a customer's bill. Exercises the
 * exact completion service (not a mocked helper) to prove: a live/trustworthy
 * route is billed, but a stale-cache-fallback or haversine route
 * (estimated()==true) is excluded from the billable distance entirely --
 * closingKM falls back to exactly the driver's own verified odometer
 * reading, and no extra distance charge is created from unreliable data.
 */
class ExternalDriverDutyServiceGarageReturnBillingTest {

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
				mock(DriverDocumentService.class),
				locationService,
				mock(ObjectProvider.class),
				mock(PaymentRepository.class),
				mock(PaymentEventAssembler.class)
		);
	}

	// distance=60km included/day, extraPerKM=50 -- deliberately low so an
	// unreliable garage-return leg (if wrongly billed) would visibly create
	// a nonzero extra-distance charge; the odometer-only distance (50km,
	// see endRequest()) alone stays under the 60km allowance.
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
	}

	// odometer reading alone = 150 - 100 = 50km, under the 60km allowance.
	private DriverDutyEndRequest endRequest() {
		return new DriverDutyEndRequest(150, null, null, Instant.now(), null, null);
	}

	@Test
	void liveTrustworthyRoute_isBilled_closingKmIncludesGarageReturnDistance() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(18.4, 1500, false, "OPEN_ROUTE_SERVICE", null));

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		// 150 + ceil(18.4)=19 => 169
		assertEquals(169, entry.getClosingKM());
	}

	@Test
	void liveTrustworthyRoute_overIncludedAllowance_createsExtraDistanceCharge() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(18.4, 1500, false, "OPEN_ROUTE_SERVICE", null));

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		// runningDistance = 169-100 = 69, over the 60km allowance by 9 ->
		// extraCharge = 9 * 50 = 450, on top of the 1000 base fare.
		assertEquals(new BigDecimal("1450.00"), entry.getDutyTotal().getAmount());
	}

	@Test
	void staleCacheFallbackRoute_isExcludedFromBilling_closingKmIsOdometerOnly() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);
		// estimated=true, real provider name preserved -- exactly
		// RouteCacheService's stale-cache degraded-mode result post-fix.
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(18.4, 1500, true, "OPEN_ROUTE_SERVICE", null));

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		// No estimated distance added -- exactly the odometer reading.
		assertEquals(150, entry.getClosingKM());
	}

	@Test
	void staleCacheFallbackRoute_cannotCreateAnExtraDistanceCharge() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(18.4, 1500, true, "OPEN_ROUTE_SERVICE", null));

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		// runningDistance = 150-100 = 50, under the 60km allowance -> no
		// extra charge, dutyTotal is exactly the base fare.
		assertEquals(new BigDecimal("1000.00"), entry.getDutyTotal().getAmount());
	}

	@Test
	void haversineFallbackRoute_cannotCreateAnExtraDistanceCharge() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenReturn(new DistanceTimeResult(18.4, 1500, true, "HAVERSINE", null));

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		assertEquals(150, entry.getClosingKM());
		assertEquals(new BigDecimal("1000.00"), entry.getDutyTotal().getAmount());
	}

	@Test
	void providerOutageWithNoCacheAtAll_stillProducesACorrectOdometerOnlyAmount() throws Exception {
		BookingEntry entry = entry();
		stubLockable(entry);
		// Total failure (no stale entry exists either) -- calculateDistanceAndTime
		// itself throws, exactly like RouteCacheService.getOrCompute propagating
		// "All geo providers failed" when there is nothing cached to fall back to.
		when(locationService.calculateDistanceAndTime(DROP, GARAGE))
				.thenThrow(new IllegalStateException("All geo providers failed"));

		service.completeDutyEntryAndFinalizeBooking(
				token(entry), endRequest(), DROP, "odometer.jpg", null, "127.0.0.1", "test-agent");

		assertEquals(150, entry.getClosingKM());
		assertEquals(new BigDecimal("1000.00"), entry.getDutyTotal().getAmount());
	}
}

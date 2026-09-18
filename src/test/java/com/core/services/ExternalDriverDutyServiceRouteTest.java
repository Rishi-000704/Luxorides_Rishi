package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.core.dtos.driverduty.DriverDutyLocationPingRequest;
import com.core.dtos.driverduty.DriverDutyLocationResponse;
import com.core.dtos.driverduty.DutyRouteLegResponse;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.location.api.DistanceTimeResult;
import com.core.location.api.GeoPoint;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.FleetVehicle;
import com.core.models.embedded.AddressSnapshot;
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
 * Covers the P2.4 on-demand route endpoint (getRouteForLeg): real
 * distance/duration/geometry for each garage-to-garage leg (A->B pickup,
 * B->C drop, C->A garage), computed via the same LocationService used for
 * the existing C->A fare-return estimate -- never fabricated, and honestly
 * "unavailable" when a waypoint or the geo provider has nothing real to
 * offer.
 */
class ExternalDriverDutyServiceRouteTest {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String RAW_TOKEN = "raw-token-value";

	private DriverDutyTokenValidator tokenValidator;
	private LocationService locationService;
	private DriverDutyLiveLocationRepository liveLocationRepository;
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		tokenValidator = mock(DriverDutyTokenValidator.class);
		locationService = mock(LocationService.class);
		liveLocationRepository = mock(DriverDutyLiveLocationRepository.class);
		when(liveLocationRepository.findByDutyId(any())).thenReturn(Optional.empty());

		service = new ExternalDriverDutyService(
				mock(BookingEntryRepository.class),
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
				mock(ApplicationEventPublisher.class),
				liveLocationRepository,
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

	private AddressSnapshot point(double lat, double lng) {
		AddressSnapshot a = new AddressSnapshot();
		a.setFormattedAddress("addr " + lat + "," + lng);
		a.setLatitude(lat);
		a.setLongitude(lng);
		return a;
	}

	private BookingEntry entryWithWaypoints() {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);

		BookingEntry entry = new BookingEntry();
		entry.setDutyId(DUTY_ID);
		entry.setBooking(booking);
		entry.setGarageLocation(point(28.60, 77.10));
		entry.setReportingLocation(point(28.55, 77.20));
		entry.setDropLocation(point(28.65, 77.30));
		return entry;
	}

	private void stubToken(BookingEntry entry) {
		DriverDutyAccessToken token = new DriverDutyAccessToken();
		token.setOrgId(ORG_ID);
		token.setDutyId(DUTY_ID);
		token.setBookingEntry(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token);
	}

	@Test
	void getRouteForLeg_returnsRealRoute_forPickupLeg() {
		BookingEntry entry = entryWithWaypoints();
		stubToken(entry);
		when(locationService.calculateDistanceAndTime(any(), any()))
				.thenReturn(new DistanceTimeResult(12.5, 1200, false, "OPEN_ROUTE_SERVICE", List.of(new GeoPoint(28.60, 77.10), new GeoPoint(28.55, 77.20))));

		DutyRouteLegResponse response = service.getRouteForLeg(RAW_TOKEN, "pickup");

		assertEquals("PICKUP", response.leg());
		assertTrue(response.available());
		assertTrue(response.routeAvailable());
		assertEquals(12.5, response.distanceKm());
		assertEquals(2, response.geometry().size());
	}

	@Test
	void getRouteForLeg_returnsRealRoute_forDropLeg() {
		BookingEntry entry = entryWithWaypoints();
		stubToken(entry);
		when(locationService.calculateDistanceAndTime(any(), any()))
				.thenReturn(new DistanceTimeResult(8.0, 900, false, "OPEN_ROUTE_SERVICE", List.of()));

		DutyRouteLegResponse response = service.getRouteForLeg(RAW_TOKEN, "DROP");

		assertEquals("DROP", response.leg());
		assertTrue(response.available());
		assertFalse(response.routeAvailable());
	}

	@Test
	void getRouteForLeg_returnsUnavailable_whenWaypointMissing() {
		BookingEntry entry = entryWithWaypoints();
		entry.setDropLocation(null);
		stubToken(entry);

		DutyRouteLegResponse response = service.getRouteForLeg(RAW_TOKEN, "garage");

		assertEquals("GARAGE", response.leg());
		assertFalse(response.available());
		assertFalse(response.routeAvailable());
	}

	@Test
	void getRouteForLeg_returnsUnavailable_whenProviderThrows() {
		BookingEntry entry = entryWithWaypoints();
		stubToken(entry);
		when(locationService.calculateDistanceAndTime(any(), any())).thenThrow(new RuntimeException("provider down"));

		DutyRouteLegResponse response = service.getRouteForLeg(RAW_TOKEN, "pickup");

		assertFalse(response.available());
	}

	@Test
	void getRouteForLeg_rejectsInvalidLeg() {
		BookingEntry entry = entryWithWaypoints();
		stubToken(entry);

		assertThrows(BusinessException.class, () -> service.getRouteForLeg(RAW_TOKEN, "NOWHERE"));
	}

	/*
	 * Root cause of the driver app's "Distance/ETA not available" on the
	 * pre-duty-start screens (Home, DutyStartMap): entry.garageLocation is
	 * only ever set inside submitStart, so before a duty starts,
	 * resolveGarageLocation always comes back empty -- the PICKUP leg must
	 * fall back to the allotted vehicle's own registered garage location
	 * instead, or every duty shows "unavailable" before it starts, forever.
	 */
	@Test
	void getRouteForLeg_pickupLeg_fallsBackToVehicleGarage_beforeDutyStart() {
		BookingEntry entry = entryWithWaypoints();
		entry.setGarageLocation(null); // pre-start: submitStart hasn't run yet

		FleetVehicle vehicle = new FleetVehicle();
		vehicle.setGarageLocation(point(28.61, 77.11));
		entry.setAllotedVehicle(vehicle);

		stubToken(entry);
		when(locationService.calculateDistanceAndTime(any(), any()))
				.thenReturn(new DistanceTimeResult(3.0, 400, false, "OPEN_ROUTE_SERVICE", List.of()));

		DutyRouteLegResponse response = service.getRouteForLeg(RAW_TOKEN, "PICKUP");

		assertTrue(response.available(), "pickup ETA should be available pre-start via the vehicle's garage location");
		assertEquals(3.0, response.distanceKm());

		ArgumentCaptor<AddressSnapshot> fromCaptor = ArgumentCaptor.forClass(AddressSnapshot.class);
		verify(locationService).calculateDistanceAndTime(fromCaptor.capture(), any());
		assertEquals(28.61, fromCaptor.getValue().getLatitude());
	}

	@Test
	void getRouteForLeg_pickupLeg_stillHonestlyUnavailable_whenNoGarageAnywhere() {
		BookingEntry entry = entryWithWaypoints();
		entry.setGarageLocation(null);
		entry.setAllotedVehicle(null); // no vehicle allotted yet either

		stubToken(entry);

		DutyRouteLegResponse response = service.getRouteForLeg(RAW_TOKEN, "PICKUP");

		assertFalse(response.available());
	}

	/*
	 * The four tests above stub calculateDistanceAndTime(any(), any()) --
	 * they prove a route comes back, but not that each leg is routed
	 * between the RIGHT two waypoints. A "reversed" or "reused" bug (e.g.
	 * DROP leg accidentally computed as garage->pickup again, or GARAGE leg
	 * computed as a mirror of PICKUP instead of an independent drop->garage
	 * call) would still pass every test above. This is the test Phase 19
	 * of the routing audit asks for: capture the exact coordinates handed
	 * to the routing provider for each leg and assert none of them get
	 * confused with each other, using three waypoints that are not
	 * symmetric (no leg's distance/bearing accidentally matches another's).
	 */
	@Test
	void everyLeg_isRoutedBetweenItsOwnWaypoints_neverReversedOrReused() {
		BookingEntry entry = entryWithWaypoints(); // garage=(28.60,77.10) pickup=(28.55,77.20) drop=(28.65,77.30)
		stubToken(entry);
		when(locationService.calculateDistanceAndTime(any(), any()))
				.thenReturn(new DistanceTimeResult(1.0, 60, false, "OPEN_ROUTE_SERVICE", List.of()));

		ArgumentCaptor<AddressSnapshot> fromCaptor = ArgumentCaptor.forClass(AddressSnapshot.class);
		ArgumentCaptor<AddressSnapshot> toCaptor = ArgumentCaptor.forClass(AddressSnapshot.class);

		service.getRouteForLeg(RAW_TOKEN, "PICKUP");
		service.getRouteForLeg(RAW_TOKEN, "DROP");
		service.getRouteForLeg(RAW_TOKEN, "GARAGE");

		verify(locationService, org.mockito.Mockito.times(3))
				.calculateDistanceAndTime(fromCaptor.capture(), toCaptor.capture());

		List<AddressSnapshot> froms = fromCaptor.getAllValues();
		List<AddressSnapshot> tos = toCaptor.getAllValues();

		// PICKUP leg: garage -> pickup (A -> B), not pickup -> garage.
		assertEquals(28.60, froms.get(0).getLatitude());
		assertEquals(28.55, tos.get(0).getLatitude());

		// DROP leg: pickup -> drop (B -> C), never re-deriving from garage.
		assertEquals(28.55, froms.get(1).getLatitude());
		assertEquals(28.65, tos.get(1).getLatitude());

		// GARAGE leg: drop -> garage (C -> A), independently -- NOT the
		// PICKUP leg run backwards (which would be pickup -> garage) and
		// NOT a reuse of the DROP leg's "to" (which would leave it at drop).
		assertEquals(28.65, froms.get(2).getLatitude());
		assertEquals(28.60, tos.get(2).getLatitude());

		// The three legs are pairwise distinct calls -- proves C->A was not
		// silently satisfied by reusing the A->B or B->C result.
		assertFalse(froms.get(2).getLatitude().equals(froms.get(0).getLatitude())
				&& tos.get(2).getLatitude().equals(tos.get(0).getLatitude()));
	}

	private DriverDutyLocationPingRequest pingAt(double lat, double lng) {
		return new DriverDutyLocationPingRequest(lat, lng, 5.0, 0.0, 12.0, Instant.now());
	}

	/*
	 * DutyStatus.RUNNING covers both garage->pickup and pickup->drop --
	 * verifies the live-ping ETA (broadcast to the customer app's tracking
	 * map, see DutyLocationChannelRegistry) targets the leg the driver is
	 * actually on, not unconditionally the final drop location.
	 */
	@Test
	void submitLocationPing_beforePickupVerified_etaTargetsPickup_notDrop() {
		BookingEntry entry = entryWithWaypoints();
		entry.setStatus(DutyStatus.RUNNING);
		entry.setPickupOtpVerifiedAt(null); // driver has not reached pickup yet
		stubToken(entry);

		// Driver is essentially AT the pickup point (28.55,77.20), but far
		// from the eventual drop (28.65,77.30, ~11.7km away).
		DriverDutyLocationResponse response = service.submitLocationPing(RAW_TOKEN, pingAt(28.5501, 77.2001));

		assertTrue(response.etaEstimated());
		assertTrue(response.distanceRemainingKm() != null && response.distanceRemainingKm() < 1.0,
				"distanceRemainingKm should be tiny (driver is at pickup), was " + response.distanceRemainingKm());
	}

	@Test
	void submitLocationPing_afterPickupVerified_etaTargetsDrop() {
		BookingEntry entry = entryWithWaypoints();
		entry.setStatus(DutyStatus.RUNNING);
		entry.setPickupOtpVerifiedAt(Instant.now()); // pickup already done, en route to drop
		stubToken(entry);

		// Driver is still at the pickup point -- now ~11.7km from drop,
		// which the response must reflect once pickup is behind them.
		DriverDutyLocationResponse response = service.submitLocationPing(RAW_TOKEN, pingAt(28.55, 77.20));

		assertTrue(response.distanceRemainingKm() != null && response.distanceRemainingKm() > 5.0,
				"distanceRemainingKm should reflect distance to drop, was " + response.distanceRemainingKm());
	}
}

package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
import com.core.models.embedded.AddressSnapshot;
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
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		tokenValidator = mock(DriverDutyTokenValidator.class);
		locationService = mock(LocationService.class);

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
}

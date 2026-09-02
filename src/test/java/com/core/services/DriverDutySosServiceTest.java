package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.driverduty.DriverDutySosRequest;
import com.core.dtos.driverduty.DriverDutySosResponse;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutySosAlert;
import com.core.repositories.DriverDutySosAlertRepository;

/*
 * Covers P2.5's SOS duplicate protection: a repeated submission for the
 * same duty within the dedupe window returns the existing alert instead of
 * inserting a new row (protects against double-tap / client retry), while a
 * submission outside the window still creates a genuinely new alert.
 */
class DriverDutySosServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String RAW_TOKEN = "raw-token-value";

	private DriverDutyTokenValidator tokenValidator;
	private DriverDutySosAlertRepository sosAlertRepository;
	private DriverDutySosService service;

	@BeforeEach
	void setUp() {
		tokenValidator = mock(DriverDutyTokenValidator.class);
		sosAlertRepository = mock(DriverDutySosAlertRepository.class);
		service = new DriverDutySosService(tokenValidator, sosAlertRepository);

		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);

		BookingEntry entry = new BookingEntry();
		entry.setDutyId(DUTY_ID);
		entry.setBooking(booking);

		DriverDutyAccessToken token = new DriverDutyAccessToken();
		token.setOrgId(ORG_ID);
		token.setDutyId(DUTY_ID);
		token.setBookingEntry(entry);
		when(tokenValidator.resolveValidToken(RAW_TOKEN)).thenReturn(token);
	}

	private DriverDutySosRequest request() {
		return new DriverDutySosRequest(28.6, 77.2, Instant.now(), "help");
	}

	@Test
	void submitSos_createsNewAlert_whenNoneRecent() {
		when(sosAlertRepository.findFirstByDutyIdAndCreatedAtAfterOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
		when(sosAlertRepository.save(any(DriverDutySosAlert.class))).thenAnswer(inv -> {
			DriverDutySosAlert alert = inv.getArgument(0);
			alert.setId("alert-1");
			return alert;
		});

		DriverDutySosResponse response = service.submitSos(RAW_TOKEN, request(), "1.2.3.4", "agent");

		assertEquals("alert-1", response.id());
		assertEquals(true, response.received());
		verify(sosAlertRepository, times(1)).save(any(DriverDutySosAlert.class));
	}

	@Test
	void submitSos_returnsExistingAlert_whenDuplicateWithinWindow() {
		DriverDutySosAlert existing = new DriverDutySosAlert();
		existing.setId("alert-existing");
		when(sosAlertRepository.findFirstByDutyIdAndCreatedAtAfterOrderByCreatedAtDesc(any(), any()))
				.thenReturn(Optional.of(existing));

		DriverDutySosResponse response = service.submitSos(RAW_TOKEN, request(), "1.2.3.4", "agent");

		assertEquals("alert-existing", response.id());
		verify(sosAlertRepository, never()).save(any(DriverDutySosAlert.class));
	}
}

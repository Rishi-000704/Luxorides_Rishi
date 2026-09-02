package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.driverduty.DriverDutyIncidentRequest;
import com.core.dtos.driverduty.DriverDutyIncidentResponse;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutyIncidentReport;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.enums.DriverDutyIncidentCategory;
import com.core.repositories.DriverDutyIncidentReportRepository;
import com.core.services.common.FileService;

/*
 * Covers P2.5's incident duplicate protection: a resubmission of the same
 * category+description for the same duty within the dedupe window returns
 * the existing report instead of creating a new one.
 */
class DriverDutyIncidentServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String RAW_TOKEN = "raw-token-value";

	private DriverDutyTokenValidator tokenValidator;
	private DriverDutyIncidentReportRepository incidentReportRepository;
	private DriverDutyIncidentService service;

	@BeforeEach
	void setUp() {
		tokenValidator = mock(DriverDutyTokenValidator.class);
		incidentReportRepository = mock(DriverDutyIncidentReportRepository.class);
		service = new DriverDutyIncidentService(tokenValidator, incidentReportRepository, mock(FileService.class));

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

	private DriverDutyIncidentRequest request() {
		AddressSnapshot location = new AddressSnapshot();
		location.setFormattedAddress("Somewhere");
		location.setLatitude(28.6);
		location.setLongitude(77.2);
		return new DriverDutyIncidentRequest(DriverDutyIncidentCategory.VEHICLE_BREAKDOWN, "Flat tyre", location, Instant.now());
	}

	@Test
	void submitIncident_createsNewReport_whenNoneRecent() throws Exception {
		when(incidentReportRepository.findFirstByDutyIdAndCategoryAndDescriptionAndCreatedAtAfterOrderByCreatedAtDesc(
				any(), any(), any(), any())).thenReturn(Optional.empty());
		when(incidentReportRepository.save(any(DriverDutyIncidentReport.class))).thenAnswer(inv -> {
			DriverDutyIncidentReport report = inv.getArgument(0);
			report.setId("incident-1");
			return report;
		});

		DriverDutyIncidentResponse response = service.submitIncident(RAW_TOKEN, request(), List.of(), "1.2.3.4", "agent");

		assertEquals("incident-1", response.id());
		assertEquals(true, response.received());
		verify(incidentReportRepository, times(1)).save(any(DriverDutyIncidentReport.class));
	}

	@Test
	void submitIncident_returnsExistingReport_whenDuplicateWithinWindow() throws Exception {
		DriverDutyIncidentReport existing = new DriverDutyIncidentReport();
		existing.setId("incident-existing");
		when(incidentReportRepository.findFirstByDutyIdAndCategoryAndDescriptionAndCreatedAtAfterOrderByCreatedAtDesc(
				any(), any(), any(), any())).thenReturn(Optional.of(existing));

		DriverDutyIncidentResponse response = service.submitIncident(RAW_TOKEN, request(), List.of(), "1.2.3.4", "agent");

		assertEquals("incident-existing", response.id());
		verify(incidentReportRepository, never()).save(any(DriverDutyIncidentReport.class));
	}
}

package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.analytics.DriverAnalyticsResponse;
import com.core.dtos.analytics.VehicleUtilizationResponse;
import com.core.models.Driver;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.embedded.Name;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.ExpenseRepository;
import com.core.repositories.FleetVehicleRepository;
import com.core.repositories.PaymentRepository;
import com.core.repositories.TripRatingRepository;

/*
 * P1.4 -- covers FleetAnalyticsService.vehicleUtilization (previously
 * 1 + N fleetVehicleRepository.findByIdAndOrgId calls) and driverAnalytics
 * (previously 1 + 3N: driverRepository.findByIdAndOrgId +
 * tripRatingRepository.findAverageStarsByDriverIdAndOrgId +
 * countByDriverIdAndOrgId, all per row). Both now use one batch call per
 * dependency instead of one call per aggregate row.
 */
class FleetAnalyticsServiceTest {

	private static final String ORG_ID = "org-1";

	private BookingEntryRepository bookingEntryRepository;
	private FleetVehicleRepository fleetVehicleRepository;
	private DriverRepository driverRepository;
	private TripRatingRepository tripRatingRepository;
	private FleetAnalyticsService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		fleetVehicleRepository = mock(FleetVehicleRepository.class);
		driverRepository = mock(DriverRepository.class);
		tripRatingRepository = mock(TripRatingRepository.class);
		PaymentRepository paymentRepository = mock(PaymentRepository.class);
		ExpenseRepository expenseRepository = mock(ExpenseRepository.class);

		service = new FleetAnalyticsService(bookingEntryRepository, fleetVehicleRepository, driverRepository,
				tripRatingRepository, paymentRepository, expenseRepository);
	}

	private FleetVehicle vehicle(String id, String regNo, String vehicleName) {
		FleetVehicle v = new FleetVehicle();
		v.setId(id);
		v.setOrgId(ORG_ID);
		v.setRegistrationNumber(regNo);
		MasterVehicle mv = new MasterVehicle();
		mv.setName(vehicleName);
		v.setMasterVehicle(mv);
		return v;
	}

	private Driver driver(String id, String firstName) {
		Driver d = new Driver();
		d.setId(id);
		d.setOrgId(ORG_ID);
		d.setName(new Name("Mr.", firstName, null));
		return d;
	}

	@Test
	void vehicleUtilization_batchesVehicleLookup_insteadOfOnePerRow() {
		when(bookingEntryRepository.aggregateVehicleUtilization(eq(ORG_ID), any(), any())).thenReturn(List.of(
				new Object[] { "v1", 5L, 120L },
				new Object[] { "v2", 3L, 60L }));

		when(fleetVehicleRepository.findByOrgIdAndIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of(
				vehicle("v1", "KA-01-AA-1111", "Sedan"),
				vehicle("v2", "KA-01-AA-2222", "SUV")));

		List<VehicleUtilizationResponse> result = service.vehicleUtilization(ORG_ID, null, null);

		verify(fleetVehicleRepository).findByOrgIdAndIdIn(eq(ORG_ID), anyCollection());
		verify(fleetVehicleRepository, never()).findByIdAndOrgId(any(), any());

		assertEquals(2, result.size());
		VehicleUtilizationResponse v1 = result.stream().filter(r -> r.fleetVehicleId().equals("v1")).findFirst().orElseThrow();
		assertEquals("Sedan", v1.vehicleName());
		assertEquals("KA-01-AA-1111", v1.registrationNumber());
		assertEquals(5L, v1.completedDuties());
		assertEquals(120L, v1.totalDistanceKm());
	}

	@Test
	void vehicleUtilization_missingVehicle_fallsBackToNullNameSafely() {
		when(bookingEntryRepository.aggregateVehicleUtilization(eq(ORG_ID), any(), any()))
				.thenReturn(List.<Object[]>of(new Object[] { "v-deleted", 2L, 40L }));
		when(fleetVehicleRepository.findByOrgIdAndIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of());

		List<VehicleUtilizationResponse> result = service.vehicleUtilization(ORG_ID, null, null);

		assertEquals(1, result.size());
		assertNull(result.get(0).vehicleName());
		assertNull(result.get(0).registrationNumber());
	}

	@Test
	void driverAnalytics_batchesDriverAndRatingLookups_insteadOfOnePerRow() {
		when(bookingEntryRepository.aggregateDriverCompletedDuties(eq(ORG_ID), any(), any())).thenReturn(List.of(
				new Object[] { "d1", 10L },
				new Object[] { "d2", 4L }));

		when(driverRepository.findByOrgIdAndIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of(
				driver("d1", "Ravi"), driver("d2", "Anita")));

		// d1 has ratings, d2 has none -- GROUP BY omits d2 entirely.
		when(tripRatingRepository.aggregateStarsByDriverIds(eq(ORG_ID), anyCollection()))
				.thenReturn(List.<Object[]>of(new Object[] { "d1", 4.5, 8L }));

		List<DriverAnalyticsResponse> result = service.driverAnalytics(ORG_ID, null, null);

		verify(driverRepository).findByOrgIdAndIdIn(eq(ORG_ID), anyCollection());
		verify(driverRepository, never()).findByIdAndOrgId(any(), any());
		verify(tripRatingRepository).aggregateStarsByDriverIds(eq(ORG_ID), anyCollection());
		verify(tripRatingRepository, never()).findAverageStarsByDriverIdAndOrgId(any(), any());
		verify(tripRatingRepository, never()).countByDriverIdAndOrgId(any(), any());

		DriverAnalyticsResponse d1 = result.stream().filter(r -> r.driverId().equals("d1")).findFirst().orElseThrow();
		assertEquals("Mr. Ravi", d1.driverName());
		assertEquals(10L, d1.completedDuties());
		assertEquals(4.5, d1.ratingAverage());
		assertEquals(8L, d1.ratingCount());

		DriverAnalyticsResponse d2 = result.stream().filter(r -> r.driverId().equals("d2")).findFirst().orElseThrow();
		assertNull(d2.ratingAverage());
		assertEquals(0L, d2.ratingCount());
	}

	@Test
	void driverAnalytics_emptyResult_doesNotCallEitherBatch() {
		when(bookingEntryRepository.aggregateDriverCompletedDuties(eq(ORG_ID), any(), any())).thenReturn(List.of());

		List<DriverAnalyticsResponse> result = service.driverAnalytics(ORG_ID, null, null);

		assertEquals(0, result.size());
		verify(driverRepository, never()).findByOrgIdAndIdIn(any(), any());
		verify(tripRatingRepository, never()).aggregateStarsByDriverIds(any(), any());
	}
}

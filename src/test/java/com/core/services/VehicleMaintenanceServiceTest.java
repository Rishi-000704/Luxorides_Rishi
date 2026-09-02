package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.maintenance.MaintenancePredictionResponse;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.VehicleMaintenanceRecord;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.FleetVehicleRepository;
import com.core.repositories.VehicleMaintenanceRecordRepository;

/*
 * P1.4 -- covers VehicleMaintenanceService.predict, previously
 * 1 (findByOrgId) + 2N (findMaxClosingKmForVehicle +
 * findFirstByFleetVehicleIdAndOrgIdOrderByServiceDateDesc, both per vehicle)
 * queries. Now 1 (findByOrgIdFetchMasterVehicle) + 1 (batch odometer max) +
 * 1 (batch service records, reduced to latest-per-vehicle in Java).
 */
class VehicleMaintenanceServiceTest {

	private static final String ORG_ID = "org-1";

	private VehicleMaintenanceRecordRepository recordRepository;
	private FleetVehicleRepository fleetVehicleRepository;
	private BookingEntryRepository bookingEntryRepository;
	private VehicleMaintenanceService service;

	@BeforeEach
	void setUp() {
		recordRepository = mock(VehicleMaintenanceRecordRepository.class);
		fleetVehicleRepository = mock(FleetVehicleRepository.class);
		bookingEntryRepository = mock(BookingEntryRepository.class);

		service = new VehicleMaintenanceService(recordRepository, fleetVehicleRepository, bookingEntryRepository);
	}

	private FleetVehicle vehicle(String id, String regNo) {
		FleetVehicle v = new FleetVehicle();
		v.setId(id);
		v.setOrgId(ORG_ID);
		v.setRegistrationNumber(regNo);
		MasterVehicle mv = new MasterVehicle();
		mv.setName("Sedan");
		v.setMasterVehicle(mv);
		return v;
	}

	private VehicleMaintenanceRecord record(String vehicleId, Instant serviceDate, int odometer) {
		VehicleMaintenanceRecord r = new VehicleMaintenanceRecord();
		r.setId("rec-" + serviceDate.toEpochMilli());
		r.setOrgId(ORG_ID);
		r.setFleetVehicleId(vehicleId);
		r.setServiceType("Oil change");
		r.setServiceDate(serviceDate);
		r.setOdometerKmAtService(odometer);
		return r;
	}

	@Test
	void predict_batchesOdometerAndServiceRecordLookups_insteadOfOnePerVehicle() {
		when(fleetVehicleRepository.findByOrgIdFetchMasterVehicle(ORG_ID))
				.thenReturn(List.of(vehicle("v1", "KA-01-1111"), vehicle("v2", "KA-01-2222")));

		when(bookingEntryRepository.findMaxClosingKmForVehicles(eq(ORG_ID), anyCollection())).thenReturn(List.of(
				new Object[] { "v1", 15000 },
				new Object[] { "v2", 3000 }));

		when(recordRepository.findByOrgIdAndFleetVehicleIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of(
				record("v1", Instant.now().minus(200, ChronoUnit.DAYS), 5000)));

		List<MaintenancePredictionResponse> result = service.predict(ORG_ID);

		verify(fleetVehicleRepository).findByOrgIdFetchMasterVehicle(ORG_ID);
		verify(fleetVehicleRepository, never()).findByOrgId(ORG_ID);
		verify(bookingEntryRepository).findMaxClosingKmForVehicles(eq(ORG_ID), anyCollection());
		verify(bookingEntryRepository, never()).findMaxClosingKmForVehicle(any(), any());
		verify(recordRepository).findByOrgIdAndFleetVehicleIdIn(eq(ORG_ID), anyCollection());
		verify(recordRepository, never()).findFirstByFleetVehicleIdAndOrgIdOrderByServiceDateDesc(any(), any());

		assertEquals(2, result.size());

		MaintenancePredictionResponse v1 = result.stream().filter(r -> r.fleetVehicleId().equals("v1")).findFirst().orElseThrow();
		assertEquals(15000, v1.currentOdometerKm());
		assertEquals(10000, v1.kmSinceLastService()); // 15000 - 5000 baseline
		// 10,000km since service (>= the 10,000km interval) makes it due,
		// independent of the days-based check.
		org.junit.jupiter.api.Assertions.assertTrue(v1.dueForService());

		MaintenancePredictionResponse v2 = result.stream().filter(r -> r.fleetVehicleId().equals("v2")).findFirst().orElseThrow();
		assertEquals(3000, v2.currentOdometerKm());
		assertEquals(3000, v2.kmSinceLastService()); // no service record -- baseline 0
		assertFalse(v2.dueForService());
	}

	@Test
	void predict_picksLatestServiceRecordPerVehicle_whenMultipleExist() {
		when(fleetVehicleRepository.findByOrgIdFetchMasterVehicle(ORG_ID)).thenReturn(List.of(vehicle("v1", "KA-01-1111")));
		when(bookingEntryRepository.findMaxClosingKmForVehicles(eq(ORG_ID), anyCollection()))
				.thenReturn(List.<Object[]>of(new Object[] { "v1", 20000 }));

		Instant older = Instant.now().minus(400, ChronoUnit.DAYS);
		Instant newer = Instant.now().minus(30, ChronoUnit.DAYS);

		when(recordRepository.findByOrgIdAndFleetVehicleIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of(
				record("v1", older, 5000),
				record("v1", newer, 18000)));

		List<MaintenancePredictionResponse> result = service.predict(ORG_ID);

		MaintenancePredictionResponse v1 = result.get(0);
		// Baseline must be 18000 (the newer record), not 5000.
		assertEquals(2000, v1.kmSinceLastService());
	}

	@Test
	void predict_vehicleWithNoOdometerData_returnsNullsSafely() {
		when(fleetVehicleRepository.findByOrgIdFetchMasterVehicle(ORG_ID)).thenReturn(List.of(vehicle("v1", "KA-01-1111")));
		when(bookingEntryRepository.findMaxClosingKmForVehicles(eq(ORG_ID), anyCollection())).thenReturn(List.of());
		when(recordRepository.findByOrgIdAndFleetVehicleIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of());

		List<MaintenancePredictionResponse> result = service.predict(ORG_ID);

		MaintenancePredictionResponse v1 = result.get(0);
		assertNull(v1.currentOdometerKm());
		assertNull(v1.kmSinceLastService());
		assertNull(v1.daysSinceLastService());
		assertFalse(v1.dueForService());
		assertFalse(v1.overdue());
	}

	@Test
	void predict_emptyFleet_doesNotCallEitherBatch() {
		when(fleetVehicleRepository.findByOrgIdFetchMasterVehicle(ORG_ID)).thenReturn(List.of());

		List<MaintenancePredictionResponse> result = service.predict(ORG_ID);

		assertEquals(0, result.size());
		verify(bookingEntryRepository, never()).findMaxClosingKmForVehicles(any(), any());
		verify(recordRepository, never()).findByOrgIdAndFleetVehicleIdIn(any(), any());
	}
}

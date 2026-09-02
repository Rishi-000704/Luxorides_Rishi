package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.dispatch.DispatchSuggestionResponse;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.DriverDutyCheckpoint;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.Name;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.OwnershipType;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.FleetVehicleRepository;
import com.core.repositories.TripRatingRepository;

/*
 * P1.5 -- covers DispatchSuggestionService.suggest/suggestForAllPendingDuties,
 * previously: existsByDriverIdAndStatusIn + checkpointRepository lookup +
 * tripRatingRepository lookup once per candidate driver, plus a full
 * org-wide aggregateDriverCompletedDuties GROUP BY re-run once per candidate
 * driver -- all of that repeated again for every pending duty via
 * suggestForAllPendingDuties. Now batched: one busy-check, one checkpoint
 * batch, one rating batch, one completed-duties aggregate per top-level
 * call (not per driver), and driver/vehicle context is built once and reused
 * across every pending duty instead of rebuilt per duty.
 */
class DispatchSuggestionServiceTest {

	private static final String ORG_ID = "org-1";

	private BookingEntryRepository bookingEntryRepository;
	private DriverRepository driverRepository;
	private FleetVehicleRepository fleetVehicleRepository;
	private DriverDutyCheckpointRepository checkpointRepository;
	private TripRatingRepository tripRatingRepository;
	private DispatchSuggestionService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		driverRepository = mock(DriverRepository.class);
		fleetVehicleRepository = mock(FleetVehicleRepository.class);
		checkpointRepository = mock(DriverDutyCheckpointRepository.class);
		tripRatingRepository = mock(TripRatingRepository.class);

		service = new DispatchSuggestionService(
				bookingEntryRepository, driverRepository, fleetVehicleRepository, checkpointRepository, tripRatingRepository);
	}

	private Driver driver(String id, String firstName) {
		Driver d = new Driver();
		d.setId(id);
		d.setOrgId(ORG_ID);
		d.setPhone("+91900000000" + id.charAt(id.length() - 1));
		d.setGender("MALE");
		d.setOwnership(OwnershipType.ORG);
		d.setName(new Name("Mr.", firstName, "Driver"));
		return d;
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

	private BookingEntry duty(String dutyId, AddressSnapshot pickup) {
		BookingEntry e = new BookingEntry();
		e.setDutyId(dutyId);
		e.setReportingLocation(pickup);
		return e;
	}

	private DriverDutyCheckpoint checkpoint(String driverId, AddressSnapshot location) {
		DriverDutyCheckpoint c = new DriverDutyCheckpoint();
		c.setDriverId(driverId);
		c.setCheckpointType(DriverDutyCheckpointType.END);
		c.setLocation(location);
		c.setSubmittedAt(Instant.now());
		return c;
	}

	private void stubEmptyFleet() {
		when(fleetVehicleRepository.findByOrgIdFetchMasterVehicle(ORG_ID)).thenReturn(List.of());
	}

	@Test
	void suggest_batchesAllDriverAndVehicleLookups_insteadOfOnePerCandidate() {
		Driver d1 = driver("d1", "Ravi");
		Driver d2 = driver("d2", "Anita");
		when(driverRepository.findByOrgId(ORG_ID)).thenReturn(List.of(d1, d2));
		when(bookingEntryRepository.findBusyDriverIds(anyCollection(), any())).thenReturn(List.of());

		when(bookingEntryRepository.aggregateDriverCompletedDuties(eq(ORG_ID), eq(null), eq(null)))
				.thenReturn(List.<Object[]>of(new Object[] { "d1", 10L }));
		when(tripRatingRepository.aggregateStarsByDriverIds(eq(ORG_ID), anyCollection()))
				.thenReturn(List.<Object[]>of(new Object[] { "d1", 4.5, 8L }));

		AddressSnapshot driverLoc = new AddressSnapshot("Driver spot", null, 12.90, 77.60);
		when(checkpointRepository.findLatestByDriverIdsAndCheckpointType(anyCollection(), eq(DriverDutyCheckpointType.END)))
				.thenReturn(List.of(checkpoint("d1", driverLoc)));

		FleetVehicle v1 = vehicle("v1", "KA-01-1111");
		when(fleetVehicleRepository.findByOrgIdFetchMasterVehicle(ORG_ID)).thenReturn(List.of(v1));
		when(bookingEntryRepository.findBusyFleetVehicleIds(anyCollection(), any())).thenReturn(List.of());
		when(bookingEntryRepository.findLastCompletedEndAtForVehicles(anyCollection())).thenReturn(List.of());

		AddressSnapshot pickup = new AddressSnapshot("Pickup", null, 12.95, 77.65);
		when(bookingEntryRepository.findByDutyIdAndOrgId("duty-1", ORG_ID)).thenReturn(Optional.of(duty("duty-1", pickup)));

		DispatchSuggestionResponse response = service.suggest("duty-1", ORG_ID);

		// Exactly one call each -- not one per driver/vehicle.
		verify(bookingEntryRepository, times(1)).aggregateDriverCompletedDuties(eq(ORG_ID), eq(null), eq(null));
		verify(tripRatingRepository, times(1)).aggregateStarsByDriverIds(eq(ORG_ID), anyCollection());
		verify(checkpointRepository, times(1))
				.findLatestByDriverIdsAndCheckpointType(anyCollection(), eq(DriverDutyCheckpointType.END));
		verify(bookingEntryRepository, times(1)).findBusyDriverIds(anyCollection(), any());
		verify(bookingEntryRepository, times(1)).findBusyFleetVehicleIds(anyCollection(), any());
		verify(bookingEntryRepository, times(1)).findLastCompletedEndAtForVehicles(anyCollection());

		verify(bookingEntryRepository, never()).existsByDriverIdAndStatusIn(any(), any());
		verify(bookingEntryRepository, never()).existsByFleetVehicleIdAndStatusIn(any(), any());
		verify(checkpointRepository, never()).findFirstByDriverIdAndCheckpointTypeOrderBySubmittedAtDesc(any(), any());
		verify(tripRatingRepository, never()).findAverageStarsByDriverIdAndOrgId(any(), any());
		verify(bookingEntryRepository, never()).findFirstByFleetVehicleIdAndStatusOrderByEndAtDesc(any(), any());
		verify(fleetVehicleRepository, never()).findByOrgId(any());

		assertEquals(2, response.drivers().size());
		var d1Suggestion = response.drivers().stream().filter(d -> d.driverId().equals("d1")).findFirst().orElseThrow();
		assertEquals(10L, d1Suggestion.completedDuties());
		assertEquals(4.5, d1Suggestion.ratingAverage());
		assertTrue(d1Suggestion.distanceKm() != null && d1Suggestion.distanceKm() > 0);

		var d2Suggestion = response.drivers().stream().filter(d -> d.driverId().equals("d2")).findFirst().orElseThrow();
		assertEquals(0L, d2Suggestion.completedDuties());
		assertEquals(null, d2Suggestion.ratingAverage());
		assertEquals(null, d2Suggestion.distanceKm());

		assertEquals(1, response.vehicles().size());
		assertEquals("v1", response.vehicles().get(0).fleetVehicleId());
		assertEquals("Sedan", response.vehicles().get(0).vehicleName());
	}

	@Test
	void suggest_excludesBusyDriversAndVehicles_sameAsTheOldPerCandidateExistsCheck() {
		Driver free = driver("d-free", "Free");
		Driver busy = driver("d-busy", "Busy");
		when(driverRepository.findByOrgId(ORG_ID)).thenReturn(List.of(free, busy));
		when(bookingEntryRepository.findBusyDriverIds(anyCollection(), any())).thenReturn(List.of("d-busy"));
		when(bookingEntryRepository.aggregateDriverCompletedDuties(eq(ORG_ID), eq(null), eq(null))).thenReturn(List.of());
		when(tripRatingRepository.aggregateStarsByDriverIds(eq(ORG_ID), anyCollection())).thenReturn(List.of());
		when(checkpointRepository.findLatestByDriverIdsAndCheckpointType(anyCollection(), any())).thenReturn(List.of());

		stubEmptyFleet();
		when(bookingEntryRepository.findByDutyIdAndOrgId("duty-1", ORG_ID))
				.thenReturn(Optional.of(duty("duty-1", new AddressSnapshot("Pickup", null, 12.9, 77.6))));

		DispatchSuggestionResponse response = service.suggest("duty-1", ORG_ID);

		assertEquals(1, response.drivers().size());
		assertEquals("d-free", response.drivers().get(0).driverId());
	}

	@Test
	void suggestForAllPendingDuties_buildsDriverAndVehicleContextOnce_notOncePerDuty() {
		Driver d1 = driver("d1", "Ravi");
		when(driverRepository.findByOrgId(ORG_ID)).thenReturn(List.of(d1));
		when(bookingEntryRepository.findBusyDriverIds(anyCollection(), any())).thenReturn(List.of());
		when(bookingEntryRepository.aggregateDriverCompletedDuties(eq(ORG_ID), eq(null), eq(null)))
				.thenReturn(List.<Object[]>of(new Object[] { "d1", 3L }));
		when(tripRatingRepository.aggregateStarsByDriverIds(eq(ORG_ID), anyCollection())).thenReturn(List.of());
		AddressSnapshot driverLoc = new AddressSnapshot("Driver spot", null, 12.90, 77.60);
		when(checkpointRepository.findLatestByDriverIdsAndCheckpointType(anyCollection(), any()))
				.thenReturn(List.of(checkpoint("d1", driverLoc)));

		FleetVehicle v1 = vehicle("v1", "KA-01-1111");
		when(fleetVehicleRepository.findByOrgIdFetchMasterVehicle(ORG_ID)).thenReturn(List.of(v1));
		when(bookingEntryRepository.findLastCompletedEndAtForVehicles(anyCollection())).thenReturn(List.of());
		when(bookingEntryRepository.findBusyFleetVehicleIds(anyCollection(), any())).thenReturn(List.of());

		AddressSnapshot pickup1 = new AddressSnapshot("Pickup 1", null, 12.9, 77.6);
		AddressSnapshot pickup2 = new AddressSnapshot("Pickup 2", null, 13.0, 77.7);
		AddressSnapshot pickup3 = new AddressSnapshot("Pickup 3", null, 13.1, 77.8);
		when(bookingEntryRepository.findRequestedDutiesForDispatch(ORG_ID)).thenReturn(List.of(
				duty("duty-1", pickup1), duty("duty-2", pickup2), duty("duty-3", pickup3)));

		var result = service.suggestForAllPendingDuties(ORG_ID);

		assertEquals(3, result.size());
		// Driver-invariant lookups happen exactly once for the whole batch of
		// 3 pending duties, not once per duty (which was the O(P x D) bug).
		verify(bookingEntryRepository, times(1)).aggregateDriverCompletedDuties(eq(ORG_ID), eq(null), eq(null));
		verify(tripRatingRepository, times(1)).aggregateStarsByDriverIds(eq(ORG_ID), anyCollection());
		verify(checkpointRepository, times(1))
				.findLatestByDriverIdsAndCheckpointType(anyCollection(), eq(DriverDutyCheckpointType.END));
		verify(driverRepository, times(1)).findByOrgId(ORG_ID);
		verify(fleetVehicleRepository, times(1)).findByOrgIdFetchMasterVehicle(ORG_ID);

		// Busy-state is still checked fresh per duty (time-sensitive), so
		// this is expected to run once per duty -- 3 times, not 1.
		verify(bookingEntryRepository, times(3)).findBusyDriverIds(anyCollection(), any());
		verify(bookingEntryRepository, times(3)).findBusyFleetVehicleIds(anyCollection(), any());

		// Every duty gets its own driver score, since distance depends on
		// that duty's own pickup location -- not identical across duties.
		double score1 = result.get("duty-1").drivers().get(0).score();
		double score2 = result.get("duty-2").drivers().get(0).score();
		assertTrue(score1 != score2, "distance-dependent score should differ by pickup location");
	}

	@Test
	void suggestForAllPendingDuties_emptyPendingDuties_stillBuildsContextButReturnsEmptyMap() {
		when(driverRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
		stubEmptyFleet();
		when(bookingEntryRepository.findRequestedDutiesForDispatch(ORG_ID)).thenReturn(List.of());

		var result = service.suggestForAllPendingDuties(ORG_ID);

		assertEquals(0, result.size());
		verify(bookingEntryRepository, never()).aggregateDriverCompletedDuties(any(), any(), any());
		verify(tripRatingRepository, never()).aggregateStarsByDriverIds(any(), any());
		verify(checkpointRepository, never()).findLatestByDriverIdsAndCheckpointType(any(), any());
	}

	@Test
	void suggest_emptyDriversAndVehicles_doesNotCallAnyBatch() {
		when(driverRepository.findByOrgId(ORG_ID)).thenReturn(List.of());
		stubEmptyFleet();
		when(bookingEntryRepository.findByDutyIdAndOrgId("duty-1", ORG_ID))
				.thenReturn(Optional.of(duty("duty-1", new AddressSnapshot("Pickup", null, 12.9, 77.6))));

		DispatchSuggestionResponse response = service.suggest("duty-1", ORG_ID);

		assertEquals(0, response.drivers().size());
		assertEquals(0, response.vehicles().size());
		verify(bookingEntryRepository, never()).findBusyDriverIds(any(), any());
		verify(bookingEntryRepository, never()).findBusyFleetVehicleIds(any(), any());
		verify(bookingEntryRepository, never()).aggregateDriverCompletedDuties(any(), any(), any());
		verify(tripRatingRepository, never()).aggregateStarsByDriverIds(any(), any());
		verify(checkpointRepository, never()).findLatestByDriverIdsAndCheckpointType(any(), any());
		verify(bookingEntryRepository, never()).findLastCompletedEndAtForVehicles(any());
	}

	@Test
	void scoreVehicle_idleDaysComputedFromLastCompletedEndAt_sameAsOldFindFirstByStatusOrderByEndAtDesc() {
		when(driverRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

		FleetVehicle idleVehicle = vehicle("v-idle", "KA-01-9999");
		when(fleetVehicleRepository.findByOrgIdFetchMasterVehicle(ORG_ID)).thenReturn(List.of(idleVehicle));
		when(bookingEntryRepository.findBusyFleetVehicleIds(anyCollection(), any())).thenReturn(List.of());

		Instant fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS);
		when(bookingEntryRepository.findLastCompletedEndAtForVehicles(anyCollection()))
				.thenReturn(List.<Object[]>of(new Object[] { "v-idle", fiveDaysAgo }));

		when(bookingEntryRepository.findByDutyIdAndOrgId("duty-1", ORG_ID))
				.thenReturn(Optional.of(duty("duty-1", new AddressSnapshot("Pickup", null, 12.9, 77.6))));

		DispatchSuggestionResponse response = service.suggest("duty-1", ORG_ID);

		assertEquals(1, response.vehicles().size());
		Double idleDays = response.vehicles().get(0).idleDays();
		assertTrue(idleDays != null && idleDays >= 4.9 && idleDays <= 5.1);
	}
}

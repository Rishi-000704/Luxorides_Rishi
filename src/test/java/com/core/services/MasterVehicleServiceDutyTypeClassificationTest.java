package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.client.app.ItineraryInput;
import com.core.dtos.client.app.VehicleValidationResponse;
import com.core.dtos.common.AddressSnapshotDTO;
import com.core.location.api.DistanceTimeResult;
import com.core.location.api.LocationService;
import com.core.mapper.VehicleAssembler;
import com.core.mapper.VehicleCatalogAssembler;
import com.core.models.CityGarage;
import com.core.models.MasterVehicle;
import com.core.models.Package;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.DutyType;
import com.core.models.enums.VehicleStatus;
import com.core.repositories.CityGarageRepository;
import com.core.repositories.MasterVehicleRepository;
import com.core.services.common.FileService;

/*
 * P0 task 6 -- the ~40km/14,400s TRANSFER->LOCAL reclassification in
 * resolveEffectiveDutyType changes which Package (and price) a customer is
 * shown and ultimately books, so it must not fire on route data that is not
 * genuinely trustworthy (a haversine guess, or a stale cache entry served
 * only because live computation just failed -- both surface as
 * DistanceTimeResult.estimated()==true after the RouteCacheService fix).
 * Exercises the real validateVehicle entrypoint; "warning" (set only when
 * the effective duty type differs from what was requested) is the
 * observable signal for whether reclassification actually happened.
 */
class MasterVehicleServiceDutyTypeClassificationTest {

	private static final String ORG_ID = "org-1";
	private static final String CLIENT_ID = "client-1";
	private static final String VEHICLE_ID = "vehicle-1";
	private static final String CITY = "BENGALURU";

	private static final AddressSnapshot GARAGE = new AddressSnapshot("Garage", null, 12.9716, 77.5946);
	private static final AddressSnapshotDTO PICKUP = new AddressSnapshotDTO("Pickup", null, 13.0000, 77.6000);
	private static final AddressSnapshotDTO DROP = new AddressSnapshotDTO("Drop", null, 13.4000, 77.9000);

	private MasterVehicleRepository masterVehicleRepository;
	private PackageService packageService;
	private LocationService locationService;
	private CityGarageRepository garageRepository;
	private MasterVehicleService service;

	@BeforeEach
	void setUp() {
		masterVehicleRepository = mock(MasterVehicleRepository.class);
		packageService = mock(PackageService.class);
		locationService = mock(LocationService.class);
		garageRepository = mock(CityGarageRepository.class);
		VehicleCatalogAssembler catalogAssembler = mock(VehicleCatalogAssembler.class);
		VehicleAssembler assembler = mock(VehicleAssembler.class);

		service = new MasterVehicleService(masterVehicleRepository, mock(FileService.class), packageService,
				catalogAssembler, locationService, garageRepository, catalogAssembler, assembler);

		MasterVehicle vehicle = new MasterVehicle();
		vehicle.setId(VEHICLE_ID);
		vehicle.setStatus(VehicleStatus.PUBLISHED);
		when(masterVehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

		when(locationService.resolveCity(any())).thenReturn(CITY);
		when(locationService.isAirport(any())).thenReturn(false);

		CityGarage garage = new CityGarage();
		garage.setOrgId(ORG_ID);
		garage.setCity(CITY);
		garage.setGarageLocation(GARAGE);
		when(garageRepository.findByOrgIdAndCity(ORG_ID, CITY)).thenReturn(Optional.of(garage));

		Package pkg = new Package();
		pkg.setId("pkg-1");
		pkg.setDutyType(DutyType.TRANSFER);
		pkg.setBaseFare(Money.INR(BigDecimal.ZERO));
		pkg.setExtraPerKM(Money.INR(BigDecimal.ZERO));
		pkg.setExtraPerHS(Money.INR(BigDecimal.ZERO));
		pkg.setNightCharge(Money.INR(BigDecimal.ZERO));
		when(packageService.getPackageByVehicleAndDutyType(anyString(), anyString(), anyString(), any(), anyString()))
				.thenReturn(pkg);
	}

	private ItineraryInput transferItinerary() {
		return new ItineraryInput(DutyType.TRANSFER, PICKUP, "2026-09-09T10:00:00Z", DROP, null);
	}

	// AddressSnapshot has no equals()/hashCode() override, so argument-value
	// matchers (eq(...)) can never match the distinct instances the real
	// code constructs -- resolveEffectiveDutyType calls
	// calculateDistanceAndTime exactly 3 times, always in this fixed order
	// (garage->pickup, pickup->drop, drop->garage), so a generic any()/any()
	// stub with sequential thenReturn(...) values reliably returns the right
	// result to the right call.
	private void stubLegs(DistanceTimeResult gToPickup, DistanceTimeResult pickupToDrop, DistanceTimeResult dropToGarage) {
		when(locationService.calculateDistanceAndTime(any(), any()))
				.thenReturn(gToPickup, pickupToDrop, dropToGarage);
	}

	@Test
	void liveTrustworthyRoute_overThreshold_reclassifiesToLocal() {
		// 20 + 20 + 20 = 60km total, all live/fresh (estimated=false).
		stubLegs(
				new DistanceTimeResult(20.0, 1200, false, "OPEN_ROUTE_SERVICE", null),
				new DistanceTimeResult(20.0, 1200, false, "OPEN_ROUTE_SERVICE", null),
				new DistanceTimeResult(20.0, 1200, false, "OPEN_ROUTE_SERVICE", null));

		VehicleValidationResponse response = service.validateVehicle(ORG_ID, CLIENT_ID, VEHICLE_ID, transferItinerary());

		assertNotNull(response.warning(), "trustworthy over-threshold data should reclassify TRANSFER -> LOCAL");
	}

	@Test
	void staleCacheFallback_overThreshold_doesNotReclassify() {
		// Same 60km total, but the middle leg came from the stale-cache
		// fallback (estimated=true, real provider name preserved) --
		// exactly RouteCacheService's degraded-mode result post-fix.
		stubLegs(
				new DistanceTimeResult(20.0, 1200, false, "OPEN_ROUTE_SERVICE", null),
				new DistanceTimeResult(20.0, 1200, true, "OPEN_ROUTE_SERVICE", null),
				new DistanceTimeResult(20.0, 1200, false, "OPEN_ROUTE_SERVICE", null));

		VehicleValidationResponse response = service.validateVehicle(ORG_ID, CLIENT_ID, VEHICLE_ID, transferItinerary());

		assertNull(response.warning(), "a stale-cache-fallback leg must not be trusted enough to reclassify");
	}

	@Test
	void haversineFallback_overThreshold_doesNotReclassify() {
		stubLegs(
				new DistanceTimeResult(20.0, 1200, true, "HAVERSINE", null),
				new DistanceTimeResult(20.0, 1200, true, "HAVERSINE", null),
				new DistanceTimeResult(20.0, 1200, true, "HAVERSINE", null));

		VehicleValidationResponse response = service.validateVehicle(ORG_ID, CLIENT_ID, VEHICLE_ID, transferItinerary());

		assertNull(response.warning(), "a haversine-fallback leg must not be trusted enough to reclassify");
	}

	@Test
	void liveTrustworthyRoute_underThreshold_staysTransfer_existingBehaviourUnchanged() {
		// 5 + 5 + 5 = 15km total, well under 40km -- normal, unaffected path.
		stubLegs(
				new DistanceTimeResult(5.0, 300, false, "OPEN_ROUTE_SERVICE", null),
				new DistanceTimeResult(5.0, 300, false, "OPEN_ROUTE_SERVICE", null),
				new DistanceTimeResult(5.0, 300, false, "OPEN_ROUTE_SERVICE", null));

		VehicleValidationResponse response = service.validateVehicle(ORG_ID, CLIENT_ID, VEHICLE_ID, transferItinerary());

		assertNull(response.warning());
		assertEquals(DutyType.TRANSFER.name(), response.selectedPackage().dutyType());
	}
}

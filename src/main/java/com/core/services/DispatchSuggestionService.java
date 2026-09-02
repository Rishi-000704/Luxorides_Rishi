package com.core.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.dispatch.DispatchDriverSuggestion;
import com.core.dtos.dispatch.DispatchSuggestionResponse;
import com.core.dtos.dispatch.DispatchVehicleSuggestion;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.DriverDutyCheckpoint;
import com.core.models.FleetVehicle;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DutyStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.FleetVehicleRepository;
import com.core.repositories.TripRatingRepository;
import com.core.util.EtaEstimator;

import lombok.RequiredArgsConstructor;

/*
 * Deterministic, explainable, real-data scoring -- this is the "smart
 * driver assignment" / "AI route optimization" capability, viewed from two
 * different roadmap categories (P4 automated dispatch, AI layer smart
 * assignment). Weighted multi-factor formula over real historical/GPS data,
 * never a trained model (no training pipeline or real-volume data exists
 * to honestly back one) -- reported as such, not oversold. Never
 * auto-assigns anything: this only ranks candidates for a dispatcher to
 * review and explicitly pick in the existing manual allot-duty flow.
 *
 * P1.5 -- scoring formula, weights, ranking, and eligibility rules below are
 * byte-for-byte unchanged from before this checkpoint. What changed is how
 * the inputs to that formula are fetched: driver/vehicle attributes that
 * don't depend on which duty is being scored (completed-duty counts, rating
 * averages, last-known GPS position, vehicle idle time) are now fetched once
 * per suggest()/suggestForAllPendingDuties() call instead of once per
 * candidate, and -- for suggestForAllPendingDuties -- once for the whole
 * pending-duty batch instead of once per duty. Busy-driver/vehicle state is
 * the one genuinely time-sensitive input, so it stays a fresh batch query
 * per duty (still one query instead of one-per-candidate) rather than being
 * folded into the shared, cross-duty context -- see buildDriverContext /
 * buildVehicleContext vs. busyDriverIds / busyVehicleIds below.
 */
@Service
@RequiredArgsConstructor
public class DispatchSuggestionService {

	private static final int TOP_N = 5;
	private static final double UNKNOWN_DISTANCE_SCORE = 50.0;
	private static final double UNKNOWN_RATING_SCORE = 25.0;
	private static final List<DutyStatus> BUSY_STATUSES = List.of(DutyStatus.ALLOTTED, DutyStatus.RUNNING);

	private final BookingEntryRepository bookingEntryRepository;
	private final DriverRepository driverRepository;
	private final FleetVehicleRepository fleetVehicleRepository;
	private final DriverDutyCheckpointRepository checkpointRepository;
	private final TripRatingRepository tripRatingRepository;

	@Transactional(readOnly = true)
	public DispatchSuggestionResponse suggest(String dutyId, String orgId) {
		BookingEntry entry = bookingEntryRepository.findByDutyIdAndOrgId(dutyId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		AddressSnapshot pickup = entry.getReportingLocation();

		DriverContext driverContext = buildDriverContext(orgId);
		VehicleContext vehicleContext = buildVehicleContext(orgId);

		return suggestForDuty(pickup, driverContext, vehicleContext);
	}

	/*
	 * Fleet-wide view: the same real per-duty scoring above, run across every
	 * currently REQUESTED duty in the org at once -- this is what "fleet-wide
	 * dispatch efficiency" (P4) / "AI route optimization" (AI layer) means
	 * here: no per-vehicle multi-stop routing exists in the booking model
	 * (confirmed -- no waypoint/multi-stop concept anywhere in the codebase),
	 * so the real, honest scope is jointly ranking candidates across all
	 * pending duties so a dispatcher can see the whole board at once, rather
	 * than one duty in isolation.
	 *
	 * P1.5 -- driverContext/vehicleContext (completed-duty counts, ratings,
	 * last-known position, vehicle idle time) are built exactly once here and
	 * reused for every pending duty below, instead of each suggest() call
	 * rebuilding them from scratch. This is what eliminates the O(pending
	 * duties x drivers) repeated-aggregate problem identified during the
	 * P1.4 audit.
	 */
	@Transactional(readOnly = true)
	public Map<String, DispatchSuggestionResponse> suggestForAllPendingDuties(String orgId) {
		List<BookingEntry> pendingDuties = bookingEntryRepository.findRequestedDutiesForDispatch(orgId);

		DriverContext driverContext = buildDriverContext(orgId);
		VehicleContext vehicleContext = buildVehicleContext(orgId);

		Map<String, DispatchSuggestionResponse> result = new LinkedHashMap<>();
		for (BookingEntry entry : pendingDuties) {
			result.put(entry.getDutyId(), suggestForDuty(entry.getReportingLocation(), driverContext, vehicleContext));
		}

		return result;
	}

	private DispatchSuggestionResponse suggestForDuty(
			AddressSnapshot pickup, DriverContext driverContext, VehicleContext vehicleContext) {

		Set<String> busyDriverIds = driverContext.driverIds().isEmpty()
				? Set.of()
				: new HashSet<>(bookingEntryRepository.findBusyDriverIds(driverContext.driverIds(), BUSY_STATUSES));

		List<DispatchDriverSuggestion> drivers = driverContext.drivers().stream()
				.filter(driver -> !busyDriverIds.contains(driver.getId()))
				.map(driver -> scoreDriver(driver, pickup, driverContext))
				.sorted(Comparator.comparingDouble(DispatchDriverSuggestion::score).reversed())
				.limit(TOP_N)
				.toList();

		Set<String> busyVehicleIds = vehicleContext.vehicleIds().isEmpty()
				? Set.of()
				: new HashSet<>(bookingEntryRepository.findBusyFleetVehicleIds(vehicleContext.vehicleIds(), BUSY_STATUSES));

		List<DispatchVehicleSuggestion> vehicles = vehicleContext.vehicles().stream()
				.filter(vehicle -> !busyVehicleIds.contains(vehicle.getId()))
				.map(vehicle -> scoreVehicle(vehicle, vehicleContext))
				.sorted(Comparator.comparingDouble(DispatchVehicleSuggestion::score).reversed())
				.limit(TOP_N)
				.toList();

		return new DispatchSuggestionResponse(drivers, vehicles);
	}

	/*
	 * P1.5 -- previously: aggregateDriverCompletedDuties(orgId, null, null),
	 * a full org-wide GROUP BY, was re-run inside scoreDriver for every single
	 * candidate driver (and, via suggestForAllPendingDuties, again for every
	 * pending duty on top of that). Now run exactly once per top-level call.
	 * Same for the per-driver rating average and last-known-position lookups,
	 * previously one query per driver.
	 */
	private DriverContext buildDriverContext(String orgId) {
		List<Driver> drivers = driverRepository.findByOrgId(orgId);
		List<String> driverIds = drivers.stream().map(Driver::getId).toList();

		Map<String, Long> completedDutiesByDriverId = driverIds.isEmpty()
				? Map.of()
				: bookingEntryRepository.aggregateDriverCompletedDuties(orgId, null, null).stream()
						.collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));

		Map<String, Double> ratingAverageByDriverId = driverIds.isEmpty()
				? Map.of()
				: tripRatingRepository.aggregateStarsByDriverIds(orgId, driverIds).stream()
						.collect(Collectors.toMap(row -> (String) row[0], row -> (Double) row[1]));

		Map<String, DriverDutyCheckpoint> lastCheckpointByDriverId = driverIds.isEmpty()
				? Map.of()
				: checkpointRepository.findLatestByDriverIdsAndCheckpointType(driverIds, DriverDutyCheckpointType.END).stream()
						.collect(Collectors.toMap(DriverDutyCheckpoint::getDriverId, Function.identity()));

		return new DriverContext(drivers, driverIds, completedDutiesByDriverId, ratingAverageByDriverId, lastCheckpointByDriverId);
	}

	/*
	 * P1.5 -- previously findByOrgId (no masterVehicle fetch), so every
	 * candidate vehicle's scoreVehicle() call triggered its own lazy load of
	 * masterVehicle for the vehicleName field -- an undocumented N+1 found
	 * while tracing this class for this checkpoint. findByOrgIdFetchMasterVehicle
	 * (added in P1.4 for VehicleMaintenanceService) has the exact same
	 * fv.orgId = :orgId scope, just with masterVehicle JOIN FETCHed, so it's a
	 * safe drop-in here without touching the plain findByOrgId that
	 * FleetVehicleDataExchangeHandler still uses unchanged. lastCompletedEndAt
	 * (idle time) is also now one batch query instead of one per vehicle.
	 */
	private VehicleContext buildVehicleContext(String orgId) {
		List<FleetVehicle> vehicles = fleetVehicleRepository.findByOrgIdFetchMasterVehicle(orgId);
		List<String> vehicleIds = vehicles.stream().map(FleetVehicle::getId).toList();

		Map<String, Instant> lastCompletedEndAtByVehicleId = vehicleIds.isEmpty()
				? Map.of()
				: bookingEntryRepository.findLastCompletedEndAtForVehicles(vehicleIds).stream()
						.collect(Collectors.toMap(row -> (String) row[0], row -> (Instant) row[1]));

		return new VehicleContext(vehicles, vehicleIds, lastCompletedEndAtByVehicleId);
	}

	private DispatchDriverSuggestion scoreDriver(Driver driver, AddressSnapshot pickup, DriverContext context) {
		DriverDutyCheckpoint lastCheckpoint = context.lastCheckpointByDriverId().get(driver.getId());
		Double distanceKm = lastCheckpoint != null && lastCheckpoint.getLocation() != null
				? lastKnownDistanceKm(lastCheckpoint.getLocation(), pickup)
				: null;

		long completedDuties = context.completedDutiesByDriverId().getOrDefault(driver.getId(), 0L);
		Double ratingAverage = context.ratingAverageByDriverId().get(driver.getId());

		double distanceScore = distanceKm != null ? Math.max(0, 100 - distanceKm) : UNKNOWN_DISTANCE_SCORE;
		double experienceScore = Math.min(completedDuties, 50);
		double ratingScore = ratingAverage != null ? ratingAverage * 10 : UNKNOWN_RATING_SCORE;

		double score = distanceScore + experienceScore + ratingScore;

		String driverName = driver.getName() != null ? driver.getName().getDisplayName() : null;

		return new DispatchDriverSuggestion(driver.getId(), driverName, distanceKm, completedDuties, ratingAverage, score);
	}

	private DispatchVehicleSuggestion scoreVehicle(FleetVehicle vehicle, VehicleContext context) {
		Instant lastUsed = context.lastCompletedEndAtByVehicleId().get(vehicle.getId());

		Double idleDays = lastUsed != null
				? Duration.between(lastUsed, Instant.now()).toHours() / 24.0
				: null;

		// A vehicle that's never been used, or has been idle longest, is
		// prioritized to balance utilization across the fleet.
		double score = idleDays != null ? Math.min(idleDays, 60) : 60.0;

		String vehicleName = vehicle.getMasterVehicle() != null ? vehicle.getMasterVehicle().getName() : null;

		return new DispatchVehicleSuggestion(
				vehicle.getId(), vehicleName, vehicle.getRegistrationNumber(), idleDays, score);
	}

	private Double lastKnownDistanceKm(AddressSnapshot from, AddressSnapshot to) {
		if (from == null || to == null || from.getLatitude() == null || to.getLatitude() == null) {
			return null;
		}

		EtaEstimator.Estimate estimate = EtaEstimator.estimate(to, from.getLatitude(), from.getLongitude(), null);
		return estimate.distanceRemainingKm();
	}

	private record DriverContext(
			List<Driver> drivers,
			List<String> driverIds,
			Map<String, Long> completedDutiesByDriverId,
			Map<String, Double> ratingAverageByDriverId,
			Map<String, DriverDutyCheckpoint> lastCheckpointByDriverId) {
	}

	private record VehicleContext(
			List<FleetVehicle> vehicles,
			List<String> vehicleIds,
			Map<String, Instant> lastCompletedEndAtByVehicleId) {
	}
}

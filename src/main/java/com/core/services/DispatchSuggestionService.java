package com.core.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
@Service
@RequiredArgsConstructor
public class DispatchSuggestionService {

	private static final int TOP_N = 5;
	private static final double UNKNOWN_DISTANCE_SCORE = 50.0;
	private static final double UNKNOWN_RATING_SCORE = 25.0;

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

		List<DispatchDriverSuggestion> drivers = driverRepository.findByOrgId(orgId).stream()
				.filter(driver -> !bookingEntryRepository.existsByDriverIdAndStatusIn(
						driver.getId(), List.of(DutyStatus.ALLOTTED, DutyStatus.RUNNING)))
				.map(driver -> scoreDriver(driver, orgId, pickup))
				.sorted(Comparator.comparingDouble(DispatchDriverSuggestion::score).reversed())
				.limit(TOP_N)
				.toList();

		List<DispatchVehicleSuggestion> vehicles = fleetVehicleRepository.findByOrgId(orgId).stream()
				.filter(vehicle -> !bookingEntryRepository.existsByFleetVehicleIdAndStatusIn(
						vehicle.getId(), List.of(DutyStatus.ALLOTTED, DutyStatus.RUNNING)))
				.map(this::scoreVehicle)
				.sorted(Comparator.comparingDouble(DispatchVehicleSuggestion::score).reversed())
				.limit(TOP_N)
				.toList();

		return new DispatchSuggestionResponse(drivers, vehicles);
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
	 */
	@Transactional(readOnly = true)
	public Map<String, DispatchSuggestionResponse> suggestForAllPendingDuties(String orgId) {
		Map<String, DispatchSuggestionResponse> result = new LinkedHashMap<>();

		for (BookingEntry entry : bookingEntryRepository.findRequestedDutiesForDispatch(orgId)) {
			result.put(entry.getDutyId(), suggest(entry.getDutyId(), orgId));
		}

		return result;
	}

	private DispatchDriverSuggestion scoreDriver(Driver driver, String orgId, AddressSnapshot pickup) {
		Double distanceKm = checkpointRepository
				.findFirstByDriverIdAndCheckpointTypeOrderBySubmittedAtDesc(driver.getId(), DriverDutyCheckpointType.END)
				.map(DriverDutyCheckpoint::getLocation)
				.map(loc -> lastKnownDistanceKm(loc, pickup))
				.orElse(null);

		long completedDuties = bookingEntryRepository.aggregateDriverCompletedDuties(orgId, null, null).stream()
				.filter(row -> driver.getId().equals(row[0]))
				.map(row -> ((Number) row[1]).longValue())
				.findFirst()
				.orElse(0L);

		Double ratingAverage = tripRatingRepository.findAverageStarsByDriverIdAndOrgId(driver.getId(), orgId);

		double distanceScore = distanceKm != null ? Math.max(0, 100 - distanceKm) : UNKNOWN_DISTANCE_SCORE;
		double experienceScore = Math.min(completedDuties, 50);
		double ratingScore = ratingAverage != null ? ratingAverage * 10 : UNKNOWN_RATING_SCORE;

		double score = distanceScore + experienceScore + ratingScore;

		String driverName = driver.getName() != null ? driver.getName().getDisplayName() : null;

		return new DispatchDriverSuggestion(driver.getId(), driverName, distanceKm, completedDuties, ratingAverage, score);
	}

	private DispatchVehicleSuggestion scoreVehicle(FleetVehicle vehicle) {
		Instant lastUsed = bookingEntryRepository
				.findFirstByFleetVehicleIdAndStatusOrderByEndAtDesc(vehicle.getId(), DutyStatus.COMPLETED)
				.map(BookingEntry::getEndAt)
				.orElse(null);

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
}

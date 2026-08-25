package com.core.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.pricing.FareRecommendationResponse;
import com.core.models.BookingEntry;
import com.core.models.enums.DutyType;
import com.core.repositories.BookingEntryRepository;

import lombok.RequiredArgsConstructor;

/*
 * Statistical advisory fare estimate -- median of real historical fares for
 * completed trips with the same org+vehicle-type+duty-type and a driven
 * distance within +/-25% of the requested one. Never replaces the static
 * Package rate card (which remains the actual pricing mechanism); this is
 * purely an informational number shown alongside it. When there isn't
 * enough real history for a bucket, it says so explicitly rather than
 * guessing.
 */
@Service
@RequiredArgsConstructor
public class FareRecommendationService {

	private static final int MIN_SAMPLE_SIZE = 3;
	private static final double DISTANCE_TOLERANCE = 0.25;

	private final BookingEntryRepository bookingEntryRepository;

	@Transactional(readOnly = true)
	public FareRecommendationResponse recommend(String orgId, String masterVehicleId, DutyType dutyType, double distanceKm) {
		List<BookingEntry> candidates = bookingEntryRepository
				.findCompletedForFareRecommendation(orgId, masterVehicleId, dutyType);

		double lowerBound = distanceKm * (1 - DISTANCE_TOLERANCE);
		double upperBound = distanceKm * (1 + DISTANCE_TOLERANCE);

		List<BigDecimal> observedFares = candidates.stream()
				.filter(e -> {
					int driven = e.getClosingKM() - e.getStartingKM();
					return driven >= lowerBound && driven <= upperBound;
				})
				.map(e -> e.getDutyTotal().getAmount())
				.sorted()
				.toList();

		if (observedFares.size() < MIN_SAMPLE_SIZE) {
			return new FareRecommendationResponse(false, observedFares.size(), null, null, null);
		}

		BigDecimal median = observedFares.get(observedFares.size() / 2);
		BigDecimal min = observedFares.get(0);
		BigDecimal max = observedFares.get(observedFares.size() - 1);

		return new FareRecommendationResponse(
				true, observedFares.size(), median.setScale(2, RoundingMode.HALF_UP), min, max);
	}
}

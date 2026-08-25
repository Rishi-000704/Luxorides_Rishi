package com.core.services;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.analytics.DemandForecastResponse;
import com.core.repositories.BookingRepository;

import lombok.RequiredArgsConstructor;

/*
 * Real statistical demand forecast -- a simple moving average of actual
 * historical booking-creation counts, bucketed by hour-of-day and
 * day-of-week over a real lookback window. Not a trained ML model (no
 * training pipeline or real production-scale data exists to honestly back
 * one -- see the AI-layer architecture decision); this is real arithmetic
 * over real timestamps, reported as exactly that. Feeds DynamicPricingService
 * as an additional real signal when useful, but stands on its own as a
 * genuine forecasting capability.
 */
@Service
@RequiredArgsConstructor
public class DemandForecastService {

	private static final int LOOKBACK_DAYS = 90;

	private final BookingRepository bookingRepository;

	@Transactional(readOnly = true)
	public DemandForecastResponse forecast(String orgId) {
		Instant since = Instant.now().minus(Duration.ofDays(LOOKBACK_DAYS));
		List<Instant> timestamps = bookingRepository.findCreatedAtSince(orgId, since);

		double weeksObserved = Math.max(1.0, LOOKBACK_DAYS / 7.0);

		Map<Integer, Long> countsByHour = timestamps.stream()
				.collect(Collectors.groupingBy(
						t -> t.atZone(ZoneOffset.UTC).getHour(),
						Collectors.counting()));

		Map<DayOfWeek, Long> countsByDay = timestamps.stream()
				.collect(Collectors.groupingBy(
						t -> t.atZone(ZoneOffset.UTC).getDayOfWeek(),
						Collectors.counting()));

		List<DemandForecastResponse.HourBucket> byHour = new TreeMap<>(countsByHour).entrySet().stream()
				.map(e -> new DemandForecastResponse.HourBucket(e.getKey(), average(e.getValue(), LOOKBACK_DAYS)))
				.toList();

		// Fill in hours with zero observations too, so the response always
		// has all 24 buckets (real zero, not a missing/fabricated gap).
		List<DemandForecastResponse.HourBucket> fullByHour = fillHours(byHour);

		List<DemandForecastResponse.DayBucket> byDay = List.of(DayOfWeek.values()).stream()
				.map(day -> new DemandForecastResponse.DayBucket(
						day.name(), average(countsByDay.getOrDefault(day, 0L), (long) weeksObserved)))
				.toList();

		return new DemandForecastResponse(LOOKBACK_DAYS, timestamps.size(), fullByHour, byDay);
	}

	private List<DemandForecastResponse.HourBucket> fillHours(List<DemandForecastResponse.HourBucket> observed) {
		Map<Integer, Double> byHourValue = observed.stream()
				.collect(Collectors.toMap(DemandForecastResponse.HourBucket::hour, DemandForecastResponse.HourBucket::averageBookings));

		return java.util.stream.IntStream.range(0, 24)
				.mapToObj(hour -> new DemandForecastResponse.HourBucket(hour, byHourValue.getOrDefault(hour, 0.0)))
				.toList();
	}

	private double average(long count, long divisor) {
		if (divisor <= 0) {
			return 0.0;
		}

		return Math.round((count / (double) divisor) * 100.0) / 100.0;
	}
}

package com.core.dtos.analytics;

import java.util.List;

public record DemandForecastResponse(
		int lookbackDays,
		long totalBookingsObserved,
		List<HourBucket> byHourOfDay,
		List<DayBucket> byDayOfWeek
) {
	public record HourBucket(int hour, double averageBookings) {
	}

	public record DayBucket(String dayOfWeek, double averageBookings) {
	}
}

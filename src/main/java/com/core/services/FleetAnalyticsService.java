package com.core.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.analytics.DriverAnalyticsResponse;
import com.core.dtos.analytics.RevenueExpenseDashboardResponse;
import com.core.dtos.analytics.VehicleUtilizationResponse;
import com.core.models.Driver;
import com.core.models.FleetVehicle;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.ExpenseRepository;
import com.core.repositories.FleetVehicleRepository;
import com.core.repositories.PaymentRepository;
import com.core.repositories.TripRatingRepository;

import lombok.RequiredArgsConstructor;

/*
 * Every number here comes from a real GROUP BY/SUM query over actual
 * BookingEntry/Payment/Expense/TripRating rows -- no estimates, no
 * fabricated fallback values. An org with thin data (the seeded dev/demo
 * data) will simply show small/zero numbers, which is the honest result.
 */
@Service
@RequiredArgsConstructor
public class FleetAnalyticsService {

	private final BookingEntryRepository bookingEntryRepository;
	private final FleetVehicleRepository fleetVehicleRepository;
	private final DriverRepository driverRepository;
	private final TripRatingRepository tripRatingRepository;
	private final PaymentRepository paymentRepository;
	private final ExpenseRepository expenseRepository;

	@Transactional(readOnly = true)
	public List<VehicleUtilizationResponse> vehicleUtilization(String orgId, Instant from, Instant to) {
		List<Object[]> rows = bookingEntryRepository.aggregateVehicleUtilization(orgId, from, to);

		/*
		 * P1.4 -- previously called fleetVehicleRepository.findByIdAndOrgId
		 * once per aggregate row (1 + N queries). Batched into one IN query
		 * (which also JOIN FETCHes masterVehicle, see FleetVehicleRepository).
		 */
		List<String> fleetVehicleIds = rows.stream().map(row -> (String) row[0]).distinct().toList();

		Map<String, FleetVehicle> vehiclesById = fleetVehicleIds.isEmpty()
				? Map.of()
				: fleetVehicleRepository.findByOrgIdAndIdIn(orgId, fleetVehicleIds).stream()
						.collect(Collectors.toMap(FleetVehicle::getId, Function.identity()));

		return rows.stream()
				.map(row -> {
					String fleetVehicleId = (String) row[0];
					long completedDuties = ((Number) row[1]).longValue();
					long totalDistanceKm = row[2] == null ? 0L : ((Number) row[2]).longValue();

					FleetVehicle vehicle = vehiclesById.get(fleetVehicleId);

					String vehicleName = vehicle != null && vehicle.getMasterVehicle() != null
							? vehicle.getMasterVehicle().getName()
							: null;

					String registrationNumber = vehicle != null ? vehicle.getRegistrationNumber() : null;

					return new VehicleUtilizationResponse(
							fleetVehicleId, vehicleName, registrationNumber, completedDuties, totalDistanceKm);
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public List<DriverAnalyticsResponse> driverAnalytics(String orgId, Instant from, Instant to) {
		List<Object[]> rows = bookingEntryRepository.aggregateDriverCompletedDuties(orgId, from, to);

		/*
		 * P1.4 -- previously called driverRepository.findByIdAndOrgId +
		 * tripRatingRepository.findAverageStarsByDriverIdAndOrgId +
		 * countByDriverIdAndOrgId once each per aggregate row (1 + 3N
		 * queries). Both batched into one IN query each.
		 */
		List<String> driverIds = rows.stream().map(row -> (String) row[0]).distinct().toList();

		Map<String, Driver> driversById = driverIds.isEmpty()
				? Map.of()
				: driverRepository.findByOrgIdAndIdIn(orgId, driverIds).stream()
						.collect(Collectors.toMap(Driver::getId, Function.identity()));

		Map<String, double[]> ratingsByDriverId = driverIds.isEmpty()
				? Map.of()
				: tripRatingRepository.aggregateStarsByDriverIds(orgId, driverIds).stream()
						.collect(Collectors.toMap(
								r -> (String) r[0],
								r -> new double[] { (Double) r[1], ((Number) r[2]).doubleValue() }));

		return rows.stream()
				.map(row -> {
					String driverId = (String) row[0];
					long completedDuties = ((Number) row[1]).longValue();

					Driver driver = driversById.get(driverId);
					String driverName = driver != null && driver.getName() != null
							? driver.getName().getDisplayName()
							: null;

					// GROUP BY omits a driver with zero ratings entirely -- same
					// null-average/0-count semantics as the single-driver methods.
					double[] rating = ratingsByDriverId.get(driverId);
					Double ratingAverage = rating != null ? rating[0] : null;
					long ratingCount = rating != null ? (long) rating[1] : 0L;

					return new DriverAnalyticsResponse(driverId, driverName, completedDuties, ratingAverage, ratingCount);
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public RevenueExpenseDashboardResponse revenueExpenseDashboard(String orgId, Instant from, Instant to) {
		BigDecimal revenue = paymentRepository.sumConfirmedByOrgIdAndDateRange(orgId, from, to);
		BigDecimal expense = expenseRepository.sumByOrgIdAndDateRange(orgId, from, to);

		revenue = revenue == null ? BigDecimal.ZERO : revenue;
		expense = expense == null ? BigDecimal.ZERO : expense;

		return new RevenueExpenseDashboardResponse(revenue, expense, revenue.subtract(expense));
	}
}

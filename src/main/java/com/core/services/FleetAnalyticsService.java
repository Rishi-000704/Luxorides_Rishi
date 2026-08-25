package com.core.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
		return bookingEntryRepository.aggregateVehicleUtilization(orgId, from, to).stream()
				.map(row -> {
					String fleetVehicleId = (String) row[0];
					long completedDuties = ((Number) row[1]).longValue();
					long totalDistanceKm = row[2] == null ? 0L : ((Number) row[2]).longValue();

					FleetVehicle vehicle = fleetVehicleRepository.findByIdAndOrgId(fleetVehicleId, orgId).orElse(null);

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
		return bookingEntryRepository.aggregateDriverCompletedDuties(orgId, from, to).stream()
				.map(row -> {
					String driverId = (String) row[0];
					long completedDuties = ((Number) row[1]).longValue();

					Driver driver = driverRepository.findByIdAndOrgId(driverId, orgId).orElse(null);
					String driverName = driver != null && driver.getName() != null
							? driver.getName().getDisplayName()
							: null;

					Double ratingAverage = tripRatingRepository.findAverageStarsByDriverIdAndOrgId(driverId, orgId);
					long ratingCount = tripRatingRepository.countByDriverIdAndOrgId(driverId, orgId);

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

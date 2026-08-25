package com.core.controllers;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.analytics.DemandForecastResponse;
import com.core.dtos.analytics.DriverAnalyticsResponse;
import com.core.dtos.analytics.RevenueExpenseDashboardResponse;
import com.core.dtos.analytics.VehicleUtilizationResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.DemandForecastService;
import com.core.services.FleetAnalyticsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/fleet/analytics")
@PreAuthorize("hasRole('EMPLOYEE') and hasAuthority('FLEET_ANALYTICS_VIEW')")
@RequiredArgsConstructor
public class FleetAnalyticsController {

	private final FleetAnalyticsService fleetAnalyticsService;
	private final DemandForecastService demandForecastService;
	private final SecurityContextUtil security;

	@GetMapping("/vehicle-utilization")
	public List<VehicleUtilizationResponse> vehicleUtilization(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return fleetAnalyticsService.vehicleUtilization(security.orgId(), from, to);
	}

	@GetMapping("/drivers")
	public List<DriverAnalyticsResponse> driverAnalytics(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return fleetAnalyticsService.driverAnalytics(security.orgId(), from, to);
	}

	@GetMapping("/revenue-expense")
	public RevenueExpenseDashboardResponse revenueExpense(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return fleetAnalyticsService.revenueExpenseDashboard(security.orgId(), from, to);
	}

	@GetMapping("/demand-forecast")
	public DemandForecastResponse demandForecast() {
		return demandForecastService.forecast(security.orgId());
	}
}

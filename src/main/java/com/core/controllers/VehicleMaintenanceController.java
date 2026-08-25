package com.core.controllers;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.maintenance.MaintenancePredictionResponse;
import com.core.dtos.maintenance.VehicleMaintenanceRecordRequest;
import com.core.dtos.maintenance.VehicleMaintenanceRecordResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.VehicleMaintenanceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/fleet/maintenance")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class VehicleMaintenanceController {

	private final VehicleMaintenanceService vehicleMaintenanceService;
	private final SecurityContextUtil security;

	@GetMapping("/predictions")
	@PreAuthorize("hasAuthority('VEHICLE_MAINTENANCE_VIEW')")
	public List<MaintenancePredictionResponse> predictions() {
		return vehicleMaintenanceService.predict(security.orgId());
	}

	@GetMapping("/vehicle/{fleetVehicleId}/history")
	@PreAuthorize("hasAuthority('VEHICLE_MAINTENANCE_VIEW')")
	public List<VehicleMaintenanceRecordResponse> history(@PathVariable String fleetVehicleId) {
		return vehicleMaintenanceService.history(security.orgId(), fleetVehicleId);
	}

	@PostMapping("/records")
	@PreAuthorize("hasAuthority('VEHICLE_MAINTENANCE_ADD')")
	public VehicleMaintenanceRecordResponse recordService(@RequestBody VehicleMaintenanceRecordRequest request) {
		return vehicleMaintenanceService.recordService(security.orgId(), request);
	}
}

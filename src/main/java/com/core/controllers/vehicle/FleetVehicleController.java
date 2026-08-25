package com.core.controllers.vehicle;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.core.dtos.vehicle.FleetVehicleDTO;
import com.core.models.FleetVehicle;
import com.core.security.SecurityContextUtil;
import com.core.services.FleetVehicleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vehicle/fleet")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class FleetVehicleController {

	private final FleetVehicleService fleetVehicleService;
	private final SecurityContextUtil security;

	/* ===================== CRUD ===================== */

	@PostMapping
	@PreAuthorize("hasAuthority('FLEET_VEHICLE_ADD')")
	public FleetVehicleDTO create(@RequestBody FleetVehicle vehicle) {
		vehicle.setOrgId(security.orgId());
		return fleetVehicleService.saveVehicle(vehicle);
	}

	@PostMapping("/update")
	@PreAuthorize("hasAuthority('FLEET_VEHICLE_EDIT')")
	public FleetVehicleDTO update(@RequestBody FleetVehicle vehicle) {
		return fleetVehicleService.update(vehicle);
	}

	@GetMapping("/{fleetVehicleId}")
	@PreAuthorize("hasAuthority('FLEET_VEHICLE_VIEW')")
	public FleetVehicleDTO get(@PathVariable String fleetVehicleId) {
		return fleetVehicleService.getFleetDTO(fleetVehicleId);
	}

	@DeleteMapping("/{fleetVehicleId}")
	@PreAuthorize("hasAuthority('FLEET_VEHICLE_DELETE')")
	public void delete(@PathVariable String fleetVehicleId) {
		fleetVehicleService.delete(fleetVehicleId);
	}

	/* ===================== SELF FLEET ===================== */

	@GetMapping("/self")
	@PreAuthorize("hasAuthority('FLEET_VEHICLE_VIEW')")
	public List<FleetVehicleDTO> getSelfFleet() {
		return fleetVehicleService.getSelfFleet(security.orgId());
	}

	/* ===================== CLIENT FLEET ===================== */

	@GetMapping("/client/{clientId}")
	@PreAuthorize("hasAuthority('FLEET_VEHICLE_VIEW')")
	public List<FleetVehicleDTO> getClientFleet(@PathVariable String clientId) {
		return fleetVehicleService.getClientFleet(clientId, security.orgId());
	}

	/* ===================== MASTER VEHICLE FLEET ===================== */

	@GetMapping("/master-vehicle/{masterVehicleId}")
	@PreAuthorize("hasAuthority('FLEET_VEHICLE_VIEW')")
	public List<FleetVehicleDTO> getFleetByMasterVehicle(@PathVariable String masterVehicleId) {
		return fleetVehicleService.getFleetByMasterVehicle(masterVehicleId, security.orgId());
	}

	/* ===================== PAGE ===================== */

	@GetMapping("/page")
	@PreAuthorize("hasAuthority('FLEET_VEHICLE_VIEW')")
	@SuppressWarnings("null")
	public Page<FleetVehicleDTO> page(@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "DESC") Sort.Direction direction) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
		return fleetVehicleService.getVehiclePage(security.orgId(), search, pageable);
	}
}

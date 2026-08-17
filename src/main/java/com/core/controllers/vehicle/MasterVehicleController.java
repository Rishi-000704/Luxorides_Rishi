package com.core.controllers.vehicle;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.vehicle.MasterVehicleDTO;
import com.core.models.MasterVehicle;
import com.core.security.SecurityContextUtil;
import com.core.services.MasterVehicleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vehicle/master")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class MasterVehicleController {

	private final MasterVehicleService masterVehicleService;
	private final SecurityContextUtil security;

	/* ===================== CRUD ===================== */

	@PostMapping
	@PreAuthorize("hasAuthority('MASTER_VEHICLE_ADD')")
	public MasterVehicleDTO create(@RequestBody MasterVehicle vehicle) {
		vehicle.setOrgId(security.orgId());
		return masterVehicleService.saveVehicle(vehicle);
	}

	@PostMapping("/update")
	@PreAuthorize("hasAuthority('MASTER_VEHICLE_EDIT')")
	public MasterVehicleDTO update(@RequestBody MasterVehicle vehicle) {
		return masterVehicleService.updateVehicle(vehicle);
	}

	@DeleteMapping("/{vehicleId}")
	@PreAuthorize("hasAuthority('MASTER_VEHICLE_DELETE')")
	public void delete(@PathVariable String vehicleId) {
		masterVehicleService.delete(vehicleId);
	}

	@GetMapping("/{vehicleId}")
	@PreAuthorize("hasAuthority('MASTER_VEHICLE_VIEW')")
	public MasterVehicleDTO get(@PathVariable String vehicleId) {
		return masterVehicleService.getMasterVehicleDTO(vehicleId);
	}

	@GetMapping("/org")
	@PreAuthorize("hasAuthority('MASTER_VEHICLE_VIEW')")
	public List<MasterVehicleDTO> getByOrg() {
		return masterVehicleService.findByOrgId(security.orgId());
	}

	/* ===================== PAGE ===================== */

	@GetMapping("/page")
	@PreAuthorize("hasAuthority('MASTER_VEHICLE_VIEW')")
	public Page<MasterVehicleDTO> page(@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "name") String sortBy,
			@RequestParam(defaultValue = "ASC") Sort.Direction direction) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
		return masterVehicleService.getVehiclePage(security.orgId(), search, pageable);
	}

	/* ===================== IMAGE ===================== */

	@PostMapping("/update-pic/{vehicleId}")
	@PreAuthorize("hasAuthority('MASTER_VEHICLE_EDIT')")
	public MasterVehicleDTO updatePic(@PathVariable String vehicleId, @RequestParam MultipartFile file)
			throws IOException {
		return masterVehicleService.updatePic(vehicleId, file);
	}
}

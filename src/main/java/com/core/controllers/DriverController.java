package com.core.controllers;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driver.DriverDTO;
import com.core.dtos.driver.DriverListItem;
import com.core.mapper.DriverAssembler;
import com.core.models.Driver;
import com.core.security.SecurityContextUtil;
import com.core.services.DriverService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/employee/drivers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class DriverController {

	private final DriverService driverService;
	private final SecurityContextUtil security;
	private final DriverAssembler assembler;

	@PostMapping
	@PreAuthorize("hasAuthority('DRIVER_ADD')")
	public DriverDTO createDriver(@RequestBody Driver driver) throws Exception {
		return assembler.assemble(driverService.saveDriver(driver, security.orgId()));
	}

	@PutMapping("/{driverId}")
	@PreAuthorize("hasAuthority('DRIVER_EDIT')")
	public DriverDTO updateDriver(@PathVariable String driverId, @RequestBody Driver driver) {
		driver.setId(driverId);
		return assembler.assemble(driverService.updateDriver(driver, security.orgId()));
	}

	@DeleteMapping("/{driverId}")
	@PreAuthorize("hasAuthority('DRIVER_DELETE')")
	public void deleteDriver(@PathVariable String driverId) {
		driverService.deleteDriver(driverId, security.orgId());
	}

	@GetMapping("/{driverId}")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public DriverDTO getDriver(@PathVariable String driverId) {
		return assembler.assemble(driverService.getDriver(driverId, security.orgId()));
	}

	@GetMapping("/client/{clientId}")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public List<DriverDTO> getDriversByClient(@PathVariable String clientId) {
		return driverService.getDriversByClient(clientId, security.orgId()).stream().map(assembler::assemble).toList();
	}

	@GetMapping("/self")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public List<DriverDTO> getSelfDrivers() {
		return driverService.getSelfDrivers(security.orgId()).stream().map(assembler::assemble).toList();
	}

	@GetMapping
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public Page<DriverListItem> getDrivers(@RequestParam(required = false) String search, Pageable pageable) {
		return assembler.assemble(driverService.getDriverPage(security.orgId(), search, pageable));
	}

	@PostMapping("/{driverId}/pic")
	@PreAuthorize("hasAuthority('DRIVER_EDIT')")
	public DriverDTO updateDriverPic(@PathVariable String driverId, @RequestParam MultipartFile file) {
		return assembler.assemble(driverService.updatePic(driverId, security.orgId(), file));
	}
}

package com.core.controllers;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.common.PackageDTO;
import com.core.models.Package;
import com.core.security.SecurityContextUtil;
import com.core.services.PackageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/package")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class PackageController {

	private final PackageService packageService;
	private final SecurityContextUtil security;
	

	/* ===================== CREATE ===================== */

	@PostMapping
	@PreAuthorize("hasAuthority('PACKAGE_ADD')")
	public PackageDTO create(@RequestBody Package pack) {
		pack.setOrgId(security.orgId());
		return packageService.save(pack);
	}

	/* ===================== UPDATE ===================== */

	@PostMapping("/update")
	@PreAuthorize("hasAuthority('PACKAGE_EDIT')")
	public PackageDTO update(@RequestBody Package pack) {
		pack.setOrgId(security.orgId());
		return packageService.update(pack);
	}

	/* ===================== DELETE ===================== */

	@DeleteMapping("/{packageId}")
	@PreAuthorize("hasAuthority('PACKAGE_DELETE')")
	public void delete(@PathVariable String packageId) {
		packageService.delete(packageId);
	}

	/* ===================== FETCH ===================== */

	@GetMapping("/{packageId}")
	@PreAuthorize("hasAuthority('PACKAGE_VIEW')")
	public PackageDTO get(@PathVariable String packageId) {
		return packageService.getPackageDTO(packageId);
	}

	/* ===================== ADMIN PAGE ===================== */

	@PostMapping("/page")
	@PreAuthorize("hasAuthority('PACKAGE_VIEW')")
	public Page<PackageDTO> page(@RequestParam(required = false) String search, Pageable pageable) {
		return packageService.getPage(security.orgId(), search, pageable);
	}

	@GetMapping("/client/{clientId}")
	@PreAuthorize("hasAuthority('PACKAGE_VIEW')")
	public List<PackageDTO> getByClient(
			@PathVariable String clientId,
			@RequestParam(required = false) Boolean forSales
	) {
		return packageService.getByClient(security.orgId(), clientId, forSales);
	}

	/* ===================== PACKAGE LIST FOR DUTY FORM ========== */

	@PostMapping("/package-list-for-duty-form")
	@PreAuthorize("hasAuthority('PACKAGE_VIEW')")
	public List<PackageDTO> resolve(@RequestParam String masterVehicleId, @RequestParam String clientId) {
		return this.packageService.getSalesPackage(security.orgId(), masterVehicleId, clientId);
	}

	@GetMapping("/master-vehicle/{masterVehicleId}")
	@PreAuthorize("hasAuthority('PACKAGE_VIEW')")
	public List<PackageDTO> getByMasterVehicle(
			@PathVariable String masterVehicleId,
			@RequestParam(required = false) Boolean forSales,
			@RequestParam(required = false) String clientId
	) {
		return packageService.getByMasterVehicle(
				security.orgId(),
				masterVehicleId,
				forSales,
				clientId
		);
	}
}

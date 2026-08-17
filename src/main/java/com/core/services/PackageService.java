package com.core.services;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.common.PackageDTO;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.mapper.PackageAssembler;
import com.core.models.Package;
import com.core.models.enums.DutyType;
import com.core.repositories.PackageRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class PackageService {

	private final PackageRepository packageRepository;
	private final PackageAssembler assembler;
	private final ClientService clientService;

	@Transactional
	public PackageDTO save(Package pack) {
		return assembler.assemble(packageRepository.save(pack));
	}

	@Transactional
	public PackageDTO update(Package input) {

		try {
			Package local = get(input.getId());

			local.setOrgId(input.getOrgId());
			local.setClientId(input.getClientId());

			local.setLocation(input.getLocation());
			local.setMasterVehicleId(input.getMasterVehicleId());
			local.setDutyType(input.getDutyType());

			local.setTime(input.getTime());
			local.setUnit(input.getUnit());
			local.setDistance(input.getDistance());

			local.setBaseFare(input.getBaseFare());
			local.setExtraPerKM(input.getExtraPerKM());
			local.setExtraPerHS(input.getExtraPerHS());
			local.setNightCharge(input.getNightCharge());

			local.setForSales(input.getForSales());

			return assembler.assemble(packageRepository.save(local));
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	@Transactional
	public void delete(String packageId) {
		packageRepository.deleteById(packageId);
	}

	@Transactional(readOnly = true)
	public Package get(String packageId) {
		return packageRepository.findById(packageId).orElseThrow(
				() -> new NotFoundException(ErrorCode.PACKAGE_NOT_FOUND, "Package not found with this Id."));
	}

	@Transactional(readOnly = true)
	public PackageDTO getPackageDTO(String packageId) {
		return assembler.assemble(this.get(packageId));
	}

	@Transactional(readOnly = true)
	public List<PackageDTO> getSalesPackage(String orgId, String masterVehicleId, String clientId) {
		return this.packageRepository.getPackageListByMasterVehicleForSales(orgId, masterVehicleId, clientId, true)
				.stream().map(assembler::assemble).toList();
	}
	/*
	 * ===================================================== PAGE (FRONTEND)
	 * =====================================================
	 */

	@Transactional(readOnly = true)
	public Page<PackageDTO> getPage(String orgId, String searchStr, Pageable pageable) {
		return this.packageRepository.getPage(orgId, searchStr, pageable).map(assembler::assemble);
	}

	@Transactional(readOnly = true)
	public List<PackageDTO> getByClient(String orgId, String clientId, Boolean forSales) {
		clientService.get(clientId, orgId);

		return packageRepository
				.findByClientForProfile(orgId, clientId, forSales)
				.stream()
				.map(assembler::assemble)
				.toList();
	}

	/*
	 * ===================================================== ATTACH TRANSIENT DATA
	 * =====================================================
	 */

	@Transactional(readOnly = true)
	public List<Package> getSalesPackagesByLocation(String orgId, String vehicleId, String clientId, String location) {
		return packageRepository.findForSalesByVehicleAndLocation(orgId, vehicleId, clientId, location);
	}

	@Transactional(readOnly = true)
	public Package getPackageByVehicleAndDutyType(String orgId, String clientId, String masterVehicleId,
			List<DutyType> dutyTypes, String location) {
		List<Package> packages = packageRepository.findEligiblePackages(orgId, clientId, masterVehicleId, dutyTypes,
				location);

		if (packages.isEmpty()) {
			throw new BusinessException(ErrorCode.PACKAGE_NOT_FOUND, "Vehicle not available for this ride.");
		}

		// 1️⃣ Prefer client-specific package
		for (Package p : packages) {
			if (clientId != null && clientId.equals(p.getClientId())) {
				return p;
			}
		}

		// 2️⃣ Fallback to first org-level package (already cheapest due to ORDER BY)
		return packages.get(0);
	}

	@Transactional(readOnly = true)
	public List<PackageDTO> getByMasterVehicle(
			String orgId,
			String masterVehicleId,
			Boolean forSales,
			String clientId
	) {
		return packageRepository
				.findByMasterVehicleForAdmin(orgId, masterVehicleId, forSales, clientId)
				.stream()
				.map(assembler::assemble)
				.toList();
	}

}

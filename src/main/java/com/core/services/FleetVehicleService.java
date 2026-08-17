package com.core.services;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.vehicle.FleetVehicleDTO;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.mapper.VehicleAssembler;
import com.core.models.FleetVehicle;
import com.core.repositories.FleetVehicleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FleetVehicleService {
	private final FleetVehicleRepository fleetVehicleRepository;
	private final VehicleAssembler assembler;

	@Transactional
	public FleetVehicleDTO saveVehicle(FleetVehicle vehicle) {

		boolean exists = fleetVehicleRepository.findByRegistrationNumber(vehicle.getRegistrationNumber()).isPresent();

		if (exists) {
			throw new BusinessException(ErrorCode.FLEET_ALREADY_EXIST,
					"Fleet with registration number %s already exists.".formatted(vehicle.getRegistrationNumber()));
		}

		return assembler.assemble(fleetVehicleRepository.save(vehicle));
	}

	@Transactional
	public FleetVehicleDTO update(FleetVehicle vehicle) {
		FleetVehicle local = this.get(vehicle.getId());
		local.setOwnership(vehicle.getOwnership());
		local.setClientId(vehicle.getClientId());
		local.setGarageLocation(vehicle.getGarageLocation());
		local.setMasterVehicleId(vehicle.getMasterVehicleId());
		local.setRegistrationNumber(vehicle.getRegistrationNumber());
		return assembler.assemble(this.fleetVehicleRepository.save(local));
	}

	@Transactional(readOnly = true)
	public FleetVehicle get(String id) {
		return this.fleetVehicleRepository.findById(id)
				.orElseThrow(() -> new NotFoundException(ErrorCode.FLEET_NOT_FOUND, "Fleet not found with this Id."));
	}

	@Transactional(readOnly = true)
	public FleetVehicleDTO getFleetDTO(String id) {
		return assembler.assemble(this.get(id));
	}

	@Transactional(readOnly = true)
	public List<FleetVehicleDTO> getSelfFleet(String orgId) {
		return this.fleetVehicleRepository.findByClientIdAndOrgId(null, orgId).stream().map(assembler::assemble)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<FleetVehicleDTO> getClientFleet(String clientId, String orgId) {
		return this.fleetVehicleRepository.findByClientIdAndOrgId(clientId, orgId).stream().map(assembler::assemble)
				.toList();
	}

	@Transactional
	public void delete(String id) {
		this.fleetVehicleRepository.deleteById(id);
	}

	@Transactional(readOnly = true)
	public Page<FleetVehicleDTO> getVehiclePage(String orgId, String searchStr, Pageable pageable) {

		return this.fleetVehicleRepository.findPageWithClient(orgId, searchStr, pageable).map(assembler::assemble);
	}

	@Transactional(readOnly = true)
	public List<FleetVehicleDTO> getFleetByMasterVehicle(String masterVehicleId, String orgId) {
		return this.fleetVehicleRepository.findByMasterVehicleIdAndOrgId(masterVehicleId, orgId).stream()
				.map(assembler::assemble).toList();
	}

}

package com.core.mapper;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.core.dtos.client.ClientDTO;
import com.core.dtos.common.AddressSnapshotDTO;
import com.core.dtos.common.NameDTO;
import com.core.dtos.vehicle.FleetVehicleDTO;
import com.core.dtos.vehicle.MasterVehicleDTO;
import com.core.models.Client;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.services.common.AuditActorService;

@Component
public class VehicleAssembler {

	private final AuditActorService auditActorService;

	public VehicleAssembler(AuditActorService auditActorService) {
		this.auditActorService = auditActorService;
	}

	/*
	 * ===================================================== MASTER VEHICLE
	 * =====================================================
	 */

	public MasterVehicleDTO assemble(MasterVehicle mv) {
		if (mv == null)
			return null;

		return new MasterVehicleDTO(mv.getId(), mv.getOrgId(),

				mv.getName(), mv.getPic(),

				mv.getFuelSystem(), mv.getFuelConsumption(), mv.getVehicleColor(), mv.getCategory(), mv.getBrand(),
				mv.getSeats(), mv.getDoors(), mv.getTransmissionType(), mv.getHorsePower(), mv.getVehicleClass(),
				mv.getModelYear(), mv.getPerformance(),

				mv.getDimension_length(), mv.getDimension_width(), mv.getDimension_height(),
				mv.getDimension_wheelbase(),

				mv.getSlug(), mv.getRating(), mv.getPopularity(), mv.getChauffeurDriven(),

				mv.getStatus(), mv.getRemarks(),

				mv.getCreatedAt(), mv.getUpdatedAt(), auditActorService.resolve(mv.getCreatedBy()).displayName(),
				auditActorService.resolve(mv.getUpdatedBy()).displayName());
	}

	public List<MasterVehicleDTO> assembleMasterVehicles(List<MasterVehicle> list) {
		if (list == null || list.isEmpty())
			return List.of();

		return list.stream().map(this::assemble).toList();
	}

	public Page<MasterVehicleDTO> assembleMasterVehicles(Page<MasterVehicle> page) {
		return page.map(this::assemble);
	}

	/*
	 * ===================================================== FLEET VEHICLE
	 * =====================================================
	 */

	public FleetVehicleDTO assemble(FleetVehicle fv) {
		if (fv == null)
			return null;

		return new FleetVehicleDTO(fv.getId(), fv.getOrgId(),

				fv.getMasterVehicleId(), fv.getClientId(),

				fv.getRegistrationNumber(), fv.getOwnership(),

				fv.getGarageLocation() == null ? null
						: new AddressSnapshotDTO(fv.getGarageLocation().getFormattedAddress(),
								fv.getGarageLocation().getGooglePlaceId(), fv.getGarageLocation().getLatitude(),
								fv.getGarageLocation().getLongitude()),

				assemble(fv.getMasterVehicle()), fv.getClient() != null ? assemble(fv.getClient()) : null,

				fv.getCreatedAt(), fv.getUpdatedAt(), auditActorService.resolve(fv.getCreatedBy()).displayName(),
				auditActorService.resolve(fv.getUpdatedBy()).displayName());
	}

	private ClientDTO assemble(Client client) {

		return new ClientDTO(client.getId(), client.getOrgId(), client.getUserId(),

				enrichName(client), client.getEmail(), client.getPhone(),

				null, client.getPic(), client.getSupplier(),

				null, null,

				client.getCreatedAt(), client.getUpdatedAt(),
				auditActorService.resolve(client.getCreatedBy()).displayName(),
				auditActorService.resolve(client.getUpdatedBy()).displayName());
	}

	private NameDTO enrichName(Client c) {
		if (c.getName() == null)
			return null;

		return new NameDTO(c.getName().getSalutation(), c.getName().getFirstName(), c.getName().getLastName());
	}

	public List<FleetVehicleDTO> assembleFleetVehicles(List<FleetVehicle> list) {
		if (list == null || list.isEmpty())
			return List.of();

		return list.stream().map(this::assemble).toList();
	}

	public Page<FleetVehicleDTO> assembleFleetVehicles(Page<FleetVehicle> page) {
		return page.map(this::assemble);
	}
}

package com.core.mapper;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.core.dtos.client.ClientBillingEntityDTO;
import com.core.dtos.client.ClientDTO;
import com.core.dtos.client.ClientListRow;
import com.core.dtos.client.PassengerDTO;
import com.core.dtos.common.DisplayAddressDTO;
import com.core.dtos.common.NameDTO;
import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.Passenger;
import com.core.models.enums.FileAccessCategory;
import com.core.services.common.AuditActorService;
import com.core.services.common.FileAccessTokenService;

@Component
public class ClientAssembler {

	private final AuditActorService auditActorService;
	private final FileAccessTokenService fileAccessTokenService;

	public ClientAssembler(AuditActorService auditActorService, FileAccessTokenService fileAccessTokenService) {
		this.auditActorService = auditActorService;
		this.fileAccessTokenService = fileAccessTokenService;
	}

	public ClientDTO assemble(Client client) {

		return new ClientDTO(client.getId(), client.getOrgId(), client.getUserId(),

				enrichName(client), client.getEmail(), client.getPhone(),

				enrichAddress(client),
				fileAccessTokenService.toAccessUrl(client.getPic(), client.getOrgId(), FileAccessCategory.PRIVATE),
				client.getSupplier(),

				enrichBillingEntities(client.getClientBillingEntity()), enrichPassengers(client.getPassengers()),

				client.getCreatedAt(), client.getUpdatedAt(),
				auditActorService.resolve(client.getCreatedBy()).displayName(),
				auditActorService.resolve(client.getUpdatedBy()).displayName());
	}

	/*
	 * ========================= Sub-mappers (private) =========================
	 */

	private NameDTO enrichName(Client c) {
		if (c.getName() == null)
			return null;

		return new NameDTO(c.getName().getSalutation(), c.getName().getFirstName(), c.getName().getLastName());
	}

	private DisplayAddressDTO enrichAddress(Client c) {
		if (c.getAddress() == null)
			return null;

		return new DisplayAddressDTO(c.getAddress().getFormattedAddress(), c.getAddress().getCity(),
				c.getAddress().getState(), c.getAddress().getPincode(), c.getAddress().getCountryCode());
	}

	public List<ClientBillingEntityDTO> enrichBillingEntities(List<ClientBillingEntity> entities) {
		if (entities == null || entities.isEmpty())
			return List.of();

		return entities.stream().map(this::enrichBillingEntity).toList();
	}

	public ClientBillingEntityDTO enrichBillingEntity(ClientBillingEntity e) {
		return new ClientBillingEntityDTO(e.getId(), e.getOrgId(), e.getBrandName(), e.getLegalName(),
				e.getAddress() == null ? null
						: new DisplayAddressDTO(e.getAddress().getFormattedAddress(), e.getAddress().getCity(),
								e.getAddress().getState(), e.getAddress().getPincode(),
								e.getAddress().getCountryCode()),
				e.getCin(), e.getGstin(), e.getPhone(), e.getEmail(), e.getBusinessType(), e.getCreatedAt(),
				e.getUpdatedAt(), auditActorService.resolve(e.getCreatedBy()).displayName(),
				auditActorService.resolve(e.getUpdatedBy()).displayName());
	}

	private List<PassengerDTO> enrichPassengers(List<Passenger> passengers) {
		if (passengers == null || passengers.isEmpty())
			return List.of();

		return passengers.stream().map(this::enrichPassenger).toList();
	}

	private PassengerDTO enrichPassenger(Passenger p) {
		return new PassengerDTO(p.getId(), p.getOrgId(), p.getClientId(),
				p.getName() == null ? null
						: new NameDTO(p.getName().getSalutation(), p.getName().getFirstName(),
								p.getName().getLastName()),
				p.getPhone(), p.getEmail(), p.getCreatedAt(), p.getUpdatedAt(),
				auditActorService.resolve(p.getCreatedBy()).displayName(),
				auditActorService.resolve(p.getUpdatedBy()).displayName());
	}

	public Page<ClientListRow> assemble(Page<Client> page) {
		return page.map(client -> new ClientListRow(client.getId(),
				client.getName() != null ? client.getName().getDisplayName() : null, client.getPhone(),
				client.getEmail(), client.getSupplier()));
	}
	
}

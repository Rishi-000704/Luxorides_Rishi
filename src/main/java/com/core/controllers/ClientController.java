package com.core.controllers;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.client.ClientBillingEntityDTO;
import com.core.dtos.client.ClientDTO;
import com.core.dtos.client.ClientListRow;
import com.core.mapper.ClientAssembler;
import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.Passenger;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/client")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class ClientController {

	private final ClientService clientService;
	private final SecurityContextUtil security;
	private final ClientAssembler clientAssembler;

	/* ===================== CREATE ===================== */

	@PostMapping
	@PreAuthorize("hasAuthority('CLIENT_ADD')")
	public ClientDTO create(@RequestBody Client client) {
		client.setOrgId(security.orgId());
		return clientAssembler.assemble(clientService.save(client, security.orgId()));
	}

	/* ===================== UPDATE ===================== */

	@PostMapping("/update")
	@PreAuthorize("hasAuthority('CLIENT_EDIT')")
	public ClientDTO update(@RequestBody Client client) {
		return clientAssembler.assemble(clientService.update(client, security.orgId()));
	}

	/* ===================== ADD BILLING ENTITY ===================== */

	@GetMapping("/add-billing-entity")
	@PreAuthorize("hasAuthority('CLIENT_EDIT')")
	public void addBillingEntity(@RequestParam String clientId, @RequestParam String clientBillingEntityId) {
		this.clientService.attachBillingEntityToClient(security.orgId(), clientId, clientBillingEntityId);
	}

	/* ===================== REMOVE BILLING ENTITY ===================== */

	@GetMapping("/remove-billing-entity")
	@PreAuthorize("hasAuthority('CLIENT_EDIT')")
	public void removeBillingEntity(@RequestParam String clientId, @RequestParam String clientBillingEntityId) {
		this.clientService.detachBillingEntityFromClient(security.orgId(), clientId, clientBillingEntityId);
	}

	/* ===================== FETCH ===================== */

	@GetMapping("/{clientId}")
	@PreAuthorize("hasAuthority('CLIENT_VIEW')")
	public ClientDTO get(@PathVariable String clientId) {
		return clientAssembler.assemble(clientService.get(clientId, security.orgId()));
	}

//	@GetMapping("/search")
//	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
//	public ClientDTO find(@RequestParam String query) {
//		return clientAssembler.assemble(clientService.findUser(query, security.orgId()));
//	}
//
	@GetMapping("/org")
	@PreAuthorize("hasAuthority('CLIENT_VIEW')")
	public List<ClientDTO> getByOrg() {
		return clientService.getByORG(security.orgId()).stream().map(clientAssembler::assemble).toList();
	}

	@GetMapping("/suppliers")
	@PreAuthorize("hasAuthority('CLIENT_VIEW')")
	public List<ClientDTO> getSuppliers() {
		return clientService.getSuppliers(security.orgId()).stream().map(clientAssembler::assemble).toList();
	}

	/* ===================== PAGE ===================== */

	@GetMapping("/page")
	@PreAuthorize("hasAuthority('CLIENT_VIEW')")
	public Page<ClientListRow> page(@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "phone") String sortBy,
			@RequestParam(defaultValue = "ASC") Sort.Direction direction) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
		return clientAssembler.assemble(clientService.getPage(security.orgId(), search, pageable));
	}

	/* ===================== PROFILE PIC ===================== */

	@PostMapping("/update-profile/{clientId}")
	@PreAuthorize("hasAuthority('CLIENT_EDIT')")
	public ClientDTO updateProfile(@PathVariable String clientId, @RequestParam MultipartFile file) {
		return clientAssembler.assemble(clientService.updateProfile(clientId, security.orgId(), file));
	}

	/* ===================== PASSENGERS ===================== */

	@PostMapping("/add-passenger")
	@PreAuthorize("hasAuthority('CLIENT_EDIT')")
	public ClientDTO addPassenger(@RequestBody Passenger passenger) {
		return clientAssembler.assemble(clientService.addPassenger(passenger, security.orgId()));
	}

	@PostMapping("/update-passenger")
	@PreAuthorize("hasAuthority('CLIENT_EDIT')")
	public ClientDTO updatePassenger(@RequestBody Passenger passenger) {
		return clientAssembler.assemble(clientService.updatePassenger(passenger, security.orgId()));
	}

	/* ===================== BILLING ENTITY ===================== */

	@GetMapping("/billing-entity/gstin/{gstin}")
	@PreAuthorize("hasAuthority('CLIENT_VIEW')")
	public ClientBillingEntityDTO getEntityByGSTIN(@PathVariable String gstin) {
		return clientAssembler.enrichBillingEntity(clientService.getByGstin(gstin, security.orgId()));
	}

	@PostMapping("/billing-entity")
	@PreAuthorize("hasAuthority('CLIENT_EDIT')")
	public ClientBillingEntityDTO saveEntity(@RequestBody ClientBillingEntity entity) {
		return clientAssembler.enrichBillingEntity(clientService.saveCorporate(entity, security.orgId()));
	}

	@PostMapping("/billing-entity/update")
	@PreAuthorize("hasAuthority('CLIENT_EDIT')")
	public ClientBillingEntityDTO updateEntity(@RequestBody ClientBillingEntity entity) {
		return clientAssembler.enrichBillingEntity(clientService.updateCorporate(entity, security.orgId()));
	}

}

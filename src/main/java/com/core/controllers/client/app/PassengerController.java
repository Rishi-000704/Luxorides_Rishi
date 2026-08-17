package com.core.controllers.client.app;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.core.models.Client;
import com.core.models.Passenger;
import com.core.models.User;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/client/app/passengers")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class PassengerController {

	private final ClientService clientService;
	 private final SecurityContextUtil security;

	/* ===================== FETCH LIST ===================== */

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<Passenger>> list() {
		Client client = clientService.findByUserId(security.userId());
		return ResponseEntity.ok(client.getPassengers());
	}

	/* ===================== ADD ===================== */

	@PostMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Client> add(@RequestBody Passenger passenger) {
		Client client = clientService.findByUserId(security.userId());

		passenger.setClientId(client.getId());
		passenger.setOrgId(security.orgId());
		return ResponseEntity.ok(clientService.addPassenger(passenger, security.orgId()));
	}

	/* ===================== UPDATE ===================== */

	@PutMapping("/{passengerId}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Client> update(@PathVariable String passengerId, @RequestBody Passenger passenger) {

		Client client = clientService.findByUserId(security.userId());

		passenger.setId(passengerId);
		passenger.setClientId(client.getId());

		return ResponseEntity.ok(clientService.updatePassenger(passenger, security.orgId()));
	}

	/* ===================== DELETE ===================== */

	@DeleteMapping("/{passengerId}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Void> delete(@PathVariable String passengerId, Authentication authentication) {
		User user = (User) authentication.getPrincipal();
		Client client = clientService.findByUserId(user.getId());

		clientService.deletePassenger(client.getId(), passengerId);
		return ResponseEntity.noContent().build();
	}
}

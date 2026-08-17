package com.core.controllers.client.app;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.client.app.ClientProfileUpdateRequest;
import com.core.models.Client;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/client/app/profile")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class ProfileController {

	private final ClientService clientService;
	private final SecurityContextUtil security;

	/* ===================== FETCH SELF ===================== */

	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Client> me() {
		Client client = clientService.findByUserId(security.userId());
		return ResponseEntity.ok(client);
	}

	/* ===================== UPDATE PROFILE INFO ===================== */

	@PutMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Client> updateProfile(@RequestBody ClientProfileUpdateRequest request) {
		Client client = clientService.findByUserId(security.userId());

		Client incoming = new Client();
		incoming.setId(client.getId());
		incoming.setName(request.getName());
		incoming.setEmail(request.getEmail());
		incoming.setAddress(request.getAddress());
		incoming.setPhone(client.getPhone()); // immutable

		return ResponseEntity.ok(clientService.update(incoming, security.orgId()));
	}

	/* ===================== UPDATE PROFILE IMAGE ===================== */

	@PostMapping("/image")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Client> updateProfileImage(@RequestParam("file") MultipartFile file) {
		Client client = clientService.findByUserId(security.userId());

		return ResponseEntity.ok(clientService.updateProfile(client.getId(), security.orgId(), file));
	}
}

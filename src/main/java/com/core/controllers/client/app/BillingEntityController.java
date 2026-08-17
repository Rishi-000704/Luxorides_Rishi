package com.core.controllers.client.app;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.User;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/client/app/billing-entities")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class BillingEntityController {

	private final SecurityContextUtil security;
	private final ClientService clientService;

	/* ===================== FETCH LIST ===================== */

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<ClientBillingEntity>> list() {
		Client client = clientService.findByUserId(security.userId());
		return ResponseEntity.ok(client.getClientBillingEntity());
	}

	/* ===================== FETCH BY GSTIN ===================== */

	@GetMapping("/by-gstin/{gstin}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ClientBillingEntity> getByGstin(@PathVariable String gstin) {
		return ResponseEntity.ok(clientService.getByGstin(gstin, security.orgId()));
	}

	/* ===================== ATTACH ===================== */

	@PostMapping("/{billingEntityId}/attach")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Client> attach(@PathVariable String billingEntityId) {
		Client client = clientService.findByUserId(security.userId());

		clientService.attachBillingEntityToClient(client.getId(), security.orgId(), billingEntityId);
		return ResponseEntity.ok(clientService.get(client.getId(), security.orgId()));
	}

	/* ===================== DETACH ===================== */

	@DeleteMapping("/{billingEntityId}/detach")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Client> detach(@PathVariable String billingEntityId, Authentication authentication) {
		User user = (User) authentication.getPrincipal();
		Client client = clientService.findByUserId(user.getId());

		clientService.detachBillingEntityFromClient(client.getId(), security.orgId(), billingEntityId);
		return ResponseEntity.ok(clientService.get(client.getId(), security.orgId()));
	}
}

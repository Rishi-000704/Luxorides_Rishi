package com.core.controllers.client.app;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.client.app.SupportMessageRequest;
import com.core.dtos.client.app.SupportTicketRequest;
import com.core.dtos.client.app.SupportTicketResponse;
import com.core.models.Client;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;
import com.core.services.SupportTicketService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/client/app/support/tickets")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SupportTicketController {

	private final SecurityContextUtil security;
	private final ClientService clientService;
	private final SupportTicketService supportTicketService;

	@GetMapping
	public List<SupportTicketResponse> list() {
		Client client = clientService.findByUserId(security.userId());
		return supportTicketService.listForClient(security.orgId(), client.getId());
	}

	@PostMapping
	public SupportTicketResponse create(@RequestBody SupportTicketRequest request) {
		Client client = clientService.findByUserId(security.userId());
		return supportTicketService.createTicket(security.orgId(), client.getId(), request);
	}

	@PostMapping("/{ticketId}/messages")
	public SupportTicketResponse addMessage(@PathVariable String ticketId, @RequestBody SupportMessageRequest request) {
		Client client = clientService.findByUserId(security.userId());
		return supportTicketService.addClientMessage(ticketId, security.orgId(), client.getId(), request.message());
	}
}

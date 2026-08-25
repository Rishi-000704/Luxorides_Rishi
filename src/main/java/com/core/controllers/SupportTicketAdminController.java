package com.core.controllers;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.client.app.SupportMessageRequest;
import com.core.dtos.client.app.SupportTicketResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.SupportTicketService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/support/tickets")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class SupportTicketAdminController {

	private final SecurityContextUtil security;
	private final SupportTicketService supportTicketService;

	@GetMapping
	@PreAuthorize("hasAuthority('SUPPORT_TICKET_VIEW')")
	public List<SupportTicketResponse> list() {
		return supportTicketService.listForOrg(security.orgId());
	}

	@PostMapping("/{ticketId}/reply")
	@PreAuthorize("hasAuthority('SUPPORT_TICKET_REPLY')")
	public SupportTicketResponse reply(@PathVariable String ticketId, @RequestBody SupportMessageRequest request) {
		return supportTicketService.addEmployeeReply(ticketId, security.orgId(), security.userId(), request.message());
	}

	@PostMapping("/{ticketId}/close")
	@PreAuthorize("hasAuthority('SUPPORT_TICKET_REPLY')")
	public SupportTicketResponse close(@PathVariable String ticketId) {
		return supportTicketService.closeTicket(ticketId, security.orgId());
	}
}

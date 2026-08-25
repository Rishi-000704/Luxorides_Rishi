package com.core.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.client.app.SupportTicketRequest;
import com.core.dtos.client.app.SupportTicketResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.SupportTicket;
import com.core.models.SupportTicketMessage;
import com.core.models.enums.SupportMessageSenderType;
import com.core.models.enums.SupportTicketStatus;
import com.core.repositories.SupportTicketMessageRepository;
import com.core.repositories.SupportTicketRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

	private final SupportTicketRepository ticketRepository;
	private final SupportTicketMessageRepository messageRepository;

	@Transactional
	public SupportTicketResponse createTicket(String orgId, String clientId, SupportTicketRequest request) {
		if (request.subject() == null || request.subject().isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Subject is required");
		}

		if (request.message() == null || request.message().isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Message is required");
		}

		SupportTicket ticket = new SupportTicket();
		ticket.setOrgId(orgId);
		ticket.setClientId(clientId);
		ticket.setSubject(request.subject());
		ticket.setStatus(SupportTicketStatus.OPEN);

		SupportTicket saved = ticketRepository.save(ticket);

		addMessage(saved, SupportMessageSenderType.CLIENT, clientId, request.message());

		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<SupportTicketResponse> listForClient(String orgId, String clientId) {
		return ticketRepository.findByOrgIdAndClientIdOrderByCreatedAtDesc(orgId, clientId)
				.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<SupportTicketResponse> listForOrg(String orgId) {
		return ticketRepository.findByOrgIdOrderByCreatedAtDesc(orgId)
				.stream().map(this::toResponse).toList();
	}

	@Transactional
	public SupportTicketResponse addClientMessage(String ticketId, String orgId, String clientId, String message) {
		SupportTicket ticket = ticketRepository.findByIdAndOrgIdAndClientId(ticketId, orgId, clientId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_TICKET_NOT_FOUND, "Ticket not found"));

		addMessage(ticket, SupportMessageSenderType.CLIENT, clientId, message);

		if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
			ticket.setStatus(SupportTicketStatus.OPEN);
			ticketRepository.save(ticket);
		}

		return toResponse(ticket);
	}

	@Transactional
	public SupportTicketResponse addEmployeeReply(String ticketId, String orgId, String employeeUserId, String message) {
		SupportTicket ticket = ticketRepository.findByIdAndOrgId(ticketId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_TICKET_NOT_FOUND, "Ticket not found"));

		addMessage(ticket, SupportMessageSenderType.EMPLOYEE, employeeUserId, message);

		ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
		ticketRepository.save(ticket);

		return toResponse(ticket);
	}

	@Transactional
	public SupportTicketResponse closeTicket(String ticketId, String orgId) {
		SupportTicket ticket = ticketRepository.findByIdAndOrgId(ticketId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_TICKET_NOT_FOUND, "Ticket not found"));

		ticket.setStatus(SupportTicketStatus.CLOSED);
		ticketRepository.save(ticket);

		return toResponse(ticket);
	}

	private void addMessage(SupportTicket ticket, SupportMessageSenderType senderType, String senderId, String message) {
		SupportTicketMessage entity = new SupportTicketMessage();
		entity.setOrgId(ticket.getOrgId());
		entity.setTicket(ticket);
		entity.setSenderType(senderType);
		entity.setSenderId(senderId);
		entity.setMessage(message);

		messageRepository.save(entity);
	}

	private SupportTicketResponse toResponse(SupportTicket ticket) {
		List<SupportTicketResponse.Message> messages = messageRepository
				.findByTicket_IdOrderByCreatedAtAsc(ticket.getId())
				.stream()
				.map(m -> new SupportTicketResponse.Message(
						m.getId(), m.getSenderType().name(), m.getMessage(), m.getCreatedAt()))
				.toList();

		return new SupportTicketResponse(ticket.getId(), ticket.getSubject(), ticket.getStatus(),
				ticket.getCreatedAt(), messages);
	}
}

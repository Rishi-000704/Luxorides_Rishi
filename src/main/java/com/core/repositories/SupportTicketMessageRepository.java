package com.core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.SupportTicketMessage;

@Repository
public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, String> {

	List<SupportTicketMessage> findByTicket_IdOrderByCreatedAtAsc(String ticketId);
}

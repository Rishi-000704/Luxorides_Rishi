package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.SupportTicket;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, String> {

	List<SupportTicket> findByOrgIdAndClientIdOrderByCreatedAtDesc(String orgId, String clientId);

	List<SupportTicket> findByOrgIdOrderByCreatedAtDesc(String orgId);

	Optional<SupportTicket> findByIdAndOrgId(String id, String orgId);

	Optional<SupportTicket> findByIdAndOrgIdAndClientId(String id, String orgId, String clientId);
}

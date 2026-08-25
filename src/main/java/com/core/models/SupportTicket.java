package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.SupportTicketStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * A real ticket thread, not a staffed live-chat system -- there is no
 * support-agent presence/queueing infrastructure in this codebase to back
 * true live chat. Messages live in SupportTicketMessage, one row per
 * message, in send order.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "support_ticket")
public class SupportTicket extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;

	@Column(nullable = false, length = 40)
	private String clientId;

	@Column(nullable = false, length = 150)
	private String subject;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupportTicketStatus status;
}

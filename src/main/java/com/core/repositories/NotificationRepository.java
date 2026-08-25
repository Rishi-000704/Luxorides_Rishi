package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.Notification;
import com.core.models.enums.NotificationRecipientType;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

	List<Notification> findByOrgIdAndRecipientTypeAndRecipientIdOrderByCreatedAtDesc(
			String orgId, NotificationRecipientType recipientType, String recipientId);

	Optional<Notification> findByIdAndOrgIdAndRecipientTypeAndRecipientId(
			String id, String orgId, NotificationRecipientType recipientType, String recipientId);

	long countByOrgIdAndRecipientTypeAndRecipientIdAndReadAtIsNull(
			String orgId, NotificationRecipientType recipientType, String recipientId);
}

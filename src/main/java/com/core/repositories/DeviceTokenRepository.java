package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DeviceToken;
import com.core.models.enums.NotificationRecipientType;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {

	Optional<DeviceToken> findByToken(String token);

	List<DeviceToken> findByOrgIdAndRecipientTypeAndRecipientId(
			String orgId, NotificationRecipientType recipientType, String recipientId);
}

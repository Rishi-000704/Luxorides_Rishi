package com.core.services;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.client.app.NotificationResponse;
import com.core.dtos.client.app.NotificationSummaryResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.DeviceToken;
import com.core.models.Notification;
import com.core.models.enums.NotificationRecipientType;
import com.core.repositories.DeviceTokenRepository;
import com.core.repositories.NotificationRepository;
import com.core.services.notification.FcmPushService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final DeviceTokenRepository deviceTokenRepository;
	private final FcmPushService fcmPushService;

	/*
	 * Called by NotificationEventListener off existing domain events -- always
	 * persists the real in-app row first, then makes a best-effort push
	 * attempt (which safely no-ops if Firebase isn't configured). Push
	 * delivery is never a precondition for the in-app feed existing.
	 */
	@Transactional
	public void create(
			String orgId,
			NotificationRecipientType recipientType,
			String recipientId,
			String title,
			String body,
			String type,
			String bookingId,
			String dutyId
	) {
		if (recipientId == null) {
			return;
		}

		Notification notification = new Notification();
		notification.setOrgId(orgId);
		notification.setRecipientType(recipientType);
		notification.setRecipientId(recipientId);
		notification.setTitle(title);
		notification.setBody(body);
		notification.setType(type);
		notification.setBookingId(bookingId);
		notification.setDutyId(dutyId);

		notificationRepository.save(notification);

		List<String> tokens = deviceTokenRepository
				.findByOrgIdAndRecipientTypeAndRecipientId(orgId, recipientType, recipientId)
				.stream()
				.map(DeviceToken::getToken)
				.toList();

		fcmPushService.send(tokens, title, body);
	}

	@Transactional(readOnly = true)
	public NotificationSummaryResponse list(String orgId, NotificationRecipientType recipientType, String recipientId) {
		List<NotificationResponse> notifications = notificationRepository
				.findByOrgIdAndRecipientTypeAndRecipientIdOrderByCreatedAtDesc(orgId, recipientType, recipientId)
				.stream()
				.map(this::toResponse)
				.toList();

		long unreadCount = notificationRepository
				.countByOrgIdAndRecipientTypeAndRecipientIdAndReadAtIsNull(orgId, recipientType, recipientId);

		return new NotificationSummaryResponse(unreadCount, notifications);
	}

	@Transactional
	public void markRead(String id, String orgId, NotificationRecipientType recipientType, String recipientId) {
		Notification notification = notificationRepository
				.findByIdAndOrgIdAndRecipientTypeAndRecipientId(id, orgId, recipientType, recipientId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND, "Notification not found"));

		if (notification.getReadAt() == null) {
			notification.setReadAt(Instant.now());
			notificationRepository.save(notification);
		}
	}

	@Transactional
	public void registerDeviceToken(
			String orgId, NotificationRecipientType recipientType, String recipientId, String token, String platform) {

		DeviceToken deviceToken = deviceTokenRepository.findByToken(token).orElseGet(DeviceToken::new);

		deviceToken.setOrgId(orgId);
		deviceToken.setRecipientType(recipientType);
		deviceToken.setRecipientId(recipientId);
		deviceToken.setToken(token);
		deviceToken.setPlatform(platform);

		deviceTokenRepository.save(deviceToken);
	}

	private NotificationResponse toResponse(Notification n) {
		return new NotificationResponse(
				n.getId(), n.getTitle(), n.getBody(), n.getType(), n.getBookingId(), n.getDutyId(),
				n.getCreatedAt(), n.getReadAt());
	}
}

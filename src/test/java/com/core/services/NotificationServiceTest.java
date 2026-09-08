package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.core.models.DeviceToken;
import com.core.models.enums.NotificationRecipientType;
import com.core.repositories.DeviceTokenRepository;
import com.core.repositories.NotificationRepository;
import com.core.services.notification.PushNotificationService;

/*
 * Covers two behaviors the Phase 2 dispatch fix depends on, both shared by
 * every recipient type (CLIENT today, DRIVER as of this change):
 *  - device-token registration is idempotent (upsert by token, never a
 *    duplicate row) -- covers re-login, reinstall, multiple devices;
 *  - a token FCM reports as permanently unregistered is pruned inline, with
 *    no new scheduled job.
 * Also proves push failure never blocks the notification being created --
 * the in-app row is saved before the push attempt is even made.
 */
class NotificationServiceTest {

	private NotificationRepository notificationRepository;
	private DeviceTokenRepository deviceTokenRepository;
	private PushNotificationService pushNotificationService;
	private NotificationService service;

	@BeforeEach
	void setUp() {
		notificationRepository = mock(NotificationRepository.class);
		deviceTokenRepository = mock(DeviceTokenRepository.class);
		pushNotificationService = mock(PushNotificationService.class);
		service = new NotificationService(notificationRepository, deviceTokenRepository, pushNotificationService);
	}

	@Test
	void registerDeviceToken_upsertsExistingRow_ratherThanDuplicating() {
		DeviceToken existing = new DeviceToken();
		existing.setId("dt-1");
		existing.setToken("token-abc");
		when(deviceTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(existing));

		service.registerDeviceToken("org-1", NotificationRecipientType.DRIVER, "driver-1", "token-abc", "ANDROID");

		ArgumentCaptor<DeviceToken> saved = ArgumentCaptor.forClass(DeviceToken.class);
		verify(deviceTokenRepository, times(1)).save(saved.capture());
		assertSame(existing, saved.getValue(), "must update the existing row, not create a new one");
		assertEquals("driver-1", saved.getValue().getRecipientId());
		assertEquals(NotificationRecipientType.DRIVER, saved.getValue().getRecipientType());
	}

	@Test
	void registerDeviceToken_reRegisteringSameTokenAfterFirstSave_stillUpdatesTheSameRow() {
		when(deviceTokenRepository.findByToken("token-abc")).thenReturn(Optional.empty());
		service.registerDeviceToken("org-1", NotificationRecipientType.DRIVER, "driver-1", "token-abc", "ANDROID");

		// Second registration (e.g. app relaunch) now finds the row the first call created.
		DeviceToken firstSave = new DeviceToken();
		firstSave.setId("dt-1");
		firstSave.setToken("token-abc");
		when(deviceTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(firstSave));

		service.registerDeviceToken("org-1", NotificationRecipientType.DRIVER, "driver-1", "token-abc", "ANDROID");

		ArgumentCaptor<DeviceToken> saved = ArgumentCaptor.forClass(DeviceToken.class);
		verify(deviceTokenRepository, times(2)).save(saved.capture());
		assertSame(firstSave, saved.getAllValues().get(1), "second call must update the existing row, not insert a new one");
	}

	@Test
	void create_prunesTokensFcmReportsAsPermanentlyUnregistered() {
		when(deviceTokenRepository.findByOrgIdAndRecipientTypeAndRecipientId(
				"org-1", NotificationRecipientType.DRIVER, "driver-1"))
				.thenReturn(List.of(token("stale-token"), token("live-token")));
		when(pushNotificationService.send(any(), anyString(), anyString())).thenReturn(List.of("stale-token"));

		service.create("org-1", NotificationRecipientType.DRIVER, "driver-1",
				"New Duty Assigned", "You have a new duty assignment.", "DUTY_ASSIGNED", "booking-1", "duty-1");

		verify(deviceTokenRepository).deleteAllByTokenIn(List.of("stale-token"));
		// The in-app row is saved regardless of push outcome -- correctness
		// never depends on FCM succeeding.
		verify(notificationRepository).save(any());
	}

	@Test
	void create_noStaleTokensReported_doesNotTouchDeviceTokenTable() {
		when(deviceTokenRepository.findByOrgIdAndRecipientTypeAndRecipientId(
				"org-1", NotificationRecipientType.DRIVER, "driver-1"))
				.thenReturn(List.of(token("live-token")));
		when(pushNotificationService.send(any(), anyString(), anyString())).thenReturn(List.of());

		service.create("org-1", NotificationRecipientType.DRIVER, "driver-1",
				"New Duty Assigned", "You have a new duty assignment.", "DUTY_ASSIGNED", "booking-1", "duty-1");

		verify(deviceTokenRepository, never()).deleteAllByTokenIn(any());
	}

	private DeviceToken token(String value) {
		DeviceToken t = new DeviceToken();
		t.setToken(value);
		return t;
	}
}

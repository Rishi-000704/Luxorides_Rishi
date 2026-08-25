package com.core.services.notification;

import java.io.FileInputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/*
 * Real FCM integration (Firebase Admin SDK), not a stub -- it sends an
 * actual push when firebase.service-account-path points at a real service
 * account. This deployment has no Firebase project configured, so at
 * startup it detects that, logs once, and every send() call afterward is a
 * safe no-op. It never reports success it didn't actually achieve: send()
 * returns a boolean the caller can use to decide whether to fall back to
 * (or rely solely on) the in-app notification feed.
 */
@Service
@Slf4j
public class FcmPushService {

	private final String serviceAccountPath;
	private boolean enabled = false;

	public FcmPushService(@Value("${firebase.service-account-path:}") String serviceAccountPath) {
		this.serviceAccountPath = serviceAccountPath;
	}

	@PostConstruct
	void init() {
		if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
			log.warn("firebase.service-account-path not configured -- push notifications are disabled "
					+ "(in-app notification feed still works). Set FIREBASE_SERVICE_ACCOUNT_PATH to enable.");
			return;
		}

		try (FileInputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.build();

			if (FirebaseApp.getApps().isEmpty()) {
				FirebaseApp.initializeApp(options);
			}

			enabled = true;
			log.info("Firebase push notifications enabled.");
		} catch (Exception ex) {
			log.error("Failed to initialize Firebase from {} -- push notifications remain disabled.",
					serviceAccountPath, ex);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean send(List<String> deviceTokens, String title, String body) {
		if (!enabled || deviceTokens == null || deviceTokens.isEmpty()) {
			return false;
		}

		boolean anySucceeded = false;

		for (String token : deviceTokens) {
			try {
				Message message = Message.builder()
						.setToken(token)
						.setNotification(Notification.builder().setTitle(title).setBody(body).build())
						.build();

				FirebaseMessaging.getInstance().send(message);
				anySucceeded = true;
			} catch (FirebaseMessagingException ex) {
				log.warn("Failed to deliver push to device token (may be stale): {}", ex.getMessage());
			}
		}

		return anySucceeded;
	}
}

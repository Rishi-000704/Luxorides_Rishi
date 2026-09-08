package com.core.services.notification;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/*
 * Real FCM integration (Firebase Admin SDK), not a stub -- it sends an
 * actual push when firebase.service-account-path points at a real service
 * account. This deployment has no Firebase project configured, so at
 * startup it detects that, logs once, and every send() call afterward is a
 * safe no-op. It never reports success it didn't actually achieve: send()
 * returns the tokens FCM reported as permanently unregistered, so the caller
 * can prune them -- push delivery itself is always best-effort and never
 * blocks the in-app notification feed, which is written first regardless.
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

	/*
	 * Returns the subset of deviceTokens that FCM reported as permanently
	 * unregistered (app uninstalled / token rotated) so the caller can prune
	 * them from DeviceToken -- the "simplest safe mechanism" for stale-token
	 * cleanup: no scheduled job, just pruning inline on the next failed send.
	 */
	public List<String> send(List<String> deviceTokens, String title, String body) {
		List<String> staleTokens = new ArrayList<>();

		if (!enabled || deviceTokens == null || deviceTokens.isEmpty()) {
			return staleTokens;
		}

		for (String token : deviceTokens) {
			try {
				Message message = Message.builder()
						.setToken(token)
						.setNotification(Notification.builder().setTitle(title).setBody(body).build())
						.build();

				FirebaseMessaging.getInstance().send(message);
			} catch (FirebaseMessagingException ex) {
				log.warn("Failed to deliver push to device token (may be stale): {}", ex.getMessage());
				if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
					staleTokens.add(token);
				}
			}
		}

		return staleTokens;
	}
}

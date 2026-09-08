package com.core.services.notification;

import java.io.FileInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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
 * Sends duty-assignment push notifications to both platforms, real
 * integrations (not stubs) for each:
 *
 *  - Android: Firebase Admin SDK, unchanged since Phase 2 -- a real send
 *    when firebase.service-account-path points at a real service account,
 *    a safe no-op otherwise.
 *  - iOS: Phase 2C. expo-notifications' iOS implementation is pure APNs
 *    (verified by reading its native source -- zero Firebase SDK
 *    involvement on-device), so the Chauffeur app registers an Expo push
 *    token on iOS instead of a raw APNs token, and this class relays it
 *    through Expo's push service (https://exp.host) rather than Firebase
 *    Admin, which only ever accepts FCM registration tokens. This needs no
 *    credential here -- Expo's basic push send requires none.
 *
 * Which transport a given token uses is decided per-token by its own shape
 * (isExpoToken), not by a stored "platform" field -- Android could in
 * principle also produce an Expo-shaped token (e.g. a future re-add of
 * Expo's relay there) and it would still route correctly with zero schema
 * change. Neither transport is ever load-bearing for correctness: send()
 * never reports success it didn't achieve, and a failure on either path
 * never blocks the in-app notification feed (written first, regardless) or
 * rolls back the duty assignment that triggered it.
 */
@Service
@Slf4j
public class PushNotificationService {

	// Expo's own documented, stable token format -- see
	// https://docs.expo.dev/push-notifications/sending-notifications/.
	private static final String EXPO_TOKEN_PATTERN = "Exponent(Push)?Token\\[.+\\]";

	private final WebClient expoPushWebClient;
	private final String serviceAccountPath;
	private boolean firebaseEnabled = false;

	public PushNotificationService(
			WebClient expoPushWebClient,
			@Value("${firebase.service-account-path:}") String serviceAccountPath) {
		this.expoPushWebClient = expoPushWebClient;
		this.serviceAccountPath = serviceAccountPath;
	}

	@PostConstruct
	void init() {
		if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
			log.warn("firebase.service-account-path not configured -- Android push is disabled "
					+ "(in-app notification feed and iOS push still work). Set FIREBASE_SERVICE_ACCOUNT_PATH to enable.");
			return;
		}

		try (FileInputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.build();

			if (FirebaseApp.getApps().isEmpty()) {
				FirebaseApp.initializeApp(options);
			}

			firebaseEnabled = true;
			log.info("Firebase push notifications enabled.");
		} catch (Exception ex) {
			log.error("Failed to initialize Firebase from {} -- Android push remains disabled.",
					serviceAccountPath, ex);
		}
	}

	/** Whether the Firebase Admin (Android) transport is configured. Expo (iOS) needs no such flag. */
	public boolean isFirebaseEnabled() {
		return firebaseEnabled;
	}

	/*
	 * Returns the subset of deviceTokens either transport reported as
	 * permanently unregistered (app uninstalled / token rotated) so the
	 * caller can prune them from DeviceToken -- the "simplest safe mechanism"
	 * for stale-token cleanup: no scheduled job, just pruning inline on the
	 * next failed send.
	 */
	public List<String> send(List<String> deviceTokens, String title, String body) {
		List<String> staleTokens = new ArrayList<>();
		if (deviceTokens == null || deviceTokens.isEmpty()) {
			return staleTokens;
		}

		List<String> expoTokens = deviceTokens.stream().filter(this::isExpoToken).toList();
		List<String> fcmTokens = deviceTokens.stream().filter(t -> !isExpoToken(t)).toList();

		staleTokens.addAll(sendViaExpo(expoTokens, title, body));
		staleTokens.addAll(sendViaFirebase(fcmTokens, title, body));
		return staleTokens;
	}

	private boolean isExpoToken(String token) {
		return token != null && token.matches(EXPO_TOKEN_PATTERN);
	}

	private List<String> sendViaFirebase(List<String> deviceTokens, String title, String body) {
		List<String> staleTokens = new ArrayList<>();
		if (!firebaseEnabled || deviceTokens.isEmpty()) {
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
				log.warn("Failed to deliver push via FCM to device token (may be stale): {}", ex.getMessage());
				if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
					staleTokens.add(token);
				}
			}
		}

		return staleTokens;
	}

	private List<String> sendViaExpo(List<String> deviceTokens, String title, String body) {
		List<String> staleTokens = new ArrayList<>();
		if (deviceTokens.isEmpty()) {
			return staleTokens;
		}

		List<ExpoPushMessage> batch = deviceTokens.stream()
				.map(token -> new ExpoPushMessage(token, title, body))
				.toList();

		ExpoPushTicketResponse response;
		try {
			response = expoPushWebClient.post()
					.uri("/send")
					.bodyValue(batch)
					.retrieve()
					.bodyToMono(ExpoPushTicketResponse.class)
					.timeout(Duration.ofSeconds(5))
					.block();
		} catch (Exception ex) {
			log.warn("Expo push API call failed -- push delivery skipped for this batch: {}", ex.getMessage());
			return staleTokens;
		}

		if (response == null || response.data() == null) {
			return staleTokens;
		}

		// Expo's response array is positionally aligned with the request batch.
		for (int i = 0; i < response.data().size() && i < deviceTokens.size(); i++) {
			ExpoPushTicket ticket = response.data().get(i);
			if (ticket == null || !"error".equals(ticket.status())) {
				continue;
			}

			log.warn("Failed to deliver push via Expo to device token (may be stale): {}", ticket.message());
			if (ticket.details() != null && "DeviceNotRegistered".equals(ticket.details().error())) {
				staleTokens.add(deviceTokens.get(i));
			}
		}

		return staleTokens;
	}

	/*
	 * Kept inside this service so that no additional response/request DTO
	 * file is required solely for the Expo push relay (same convention as
	 * GoogleGeoProvider's private response records).
	 */
	private record ExpoPushMessage(String to, String title, String body) {
	}

	private record ExpoPushTicketResponse(List<ExpoPushTicket> data) {
	}

	private record ExpoPushTicket(String status, String id, String message, ExpoPushTicketDetails details) {
	}

	private record ExpoPushTicketDetails(String error) {
	}
}

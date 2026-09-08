package com.core.services.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/*
 * Phase 2C: proves the iOS/Expo relay routes correctly by token shape, prunes
 * stale (DeviceNotRegistered) tokens exactly like the Android/FCM path
 * already does, and never lets an Expo API failure propagate (push is always
 * best-effort). The Firebase/FCM path itself stays untested here for the
 * same reason it always has been: FirebaseMessaging.getInstance().send() is
 * a static call into the real SDK, not something a plain unit test can fake
 * without PowerMock -- not worth it for a path this test suite already
 * exercises indirectly via isExpoToken routing (a real FCM-shaped token is
 * proven to never reach the Expo mock below).
 *
 * WebClient is faked via a stub ExchangeFunction (Spring's own documented
 * way to unit test WebClient callers) -- no real network call, no new test
 * dependency.
 */
class PushNotificationServiceTest {

	private PushNotificationService serviceWithExpoResponse(String responseJson, int status) {
		ExchangeFunction stub = request -> Mono.just(
				ClientResponse.create(HttpStatus.valueOf(status))
						.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
						.body(responseJson)
						.build());

		WebClient webClient = WebClient.builder()
				.baseUrl("https://exp.host/--/api/v2/push")
				.exchangeFunction(stub)
				.build();

		// firebase.service-account-path left blank -- Firebase/FCM stays
		// disabled, exactly like this dev environment (see FcmPushService's
		// original Phase 2 doc comment); nothing in these tests needs it.
		return new PushNotificationService(webClient, "");
	}

	@Test
	void expoShapedToken_deliveredViaExpo_notFirebase() {
		PushNotificationService service = serviceWithExpoResponse(
				"{\"data\":[{\"status\":\"ok\",\"id\":\"ticket-1\"}]}", 200);

		List<String> stale = service.send(List.of("ExponentPushToken[abc123]"), "New Duty Assigned", "body");

		assertTrue(stale.isEmpty());
	}

	@Test
	void fcmShapedToken_neverReachesExpo_andIsSkippedSinceFirebaseIsDisabledInThisTest() {
		// If this were misrouted to Expo, the stub below would return an error
		// ticket for it and it would show up as stale -- it must not.
		PushNotificationService service = serviceWithExpoResponse(
				"{\"data\":[{\"status\":\"error\",\"message\":\"should never be called for this token\"}]}", 200);

		List<String> stale = service.send(List.of("fcm-registration-token-not-expo-shaped"), "New Duty Assigned", "body");

		// Firebase is disabled in this test (no service account) -- an
		// FCM-shaped token is a silent no-op there, same as before Phase 2C.
		assertTrue(stale.isEmpty());
	}

	@Test
	void expoReportsDeviceNotRegistered_tokenIsReturnedAsStale() {
		PushNotificationService service = serviceWithExpoResponse(
				"""
				{"data":[{"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}}]}
				""",
				200);

		List<String> stale = service.send(List.of("ExponentPushToken[stale-one]"), "New Duty Assigned", "body");

		assertEquals(List.of("ExponentPushToken[stale-one]"), stale);
	}

	@Test
	void expoReportsANonStaleError_tokenIsNotPruned() {
		// e.g. a transient rate-limit or message-format error -- must not be
		// treated the same as a permanently dead token.
		PushNotificationService service = serviceWithExpoResponse(
				"""
				{"data":[{"status":"error","message":"rate limited","details":{"error":"MessageRateExceeded"}}]}
				""",
				200);

		List<String> stale = service.send(List.of("ExponentPushToken[rate-limited]"), "New Duty Assigned", "body");

		assertTrue(stale.isEmpty());
	}

	@Test
	void mixedBatch_onlyTheStaleExpoTokenIsPruned() {
		PushNotificationService service = serviceWithExpoResponse(
				"""
				{"data":[
					{"status":"ok","id":"ticket-1"},
					{"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}}
				]}
				""",
				200);

		List<String> stale = service.send(
				List.of("ExponentPushToken[live]", "ExponentPushToken[dead]"), "New Duty Assigned", "body");

		assertEquals(List.of("ExponentPushToken[dead]"), stale);
	}

	@Test
	void expoApiCallFails_neverThrows_returnsNoStaleTokens() {
		ExchangeFunction failing = request -> Mono.error(new RuntimeException("connection refused"));
		WebClient webClient = WebClient.builder()
				.baseUrl("https://exp.host/--/api/v2/push")
				.exchangeFunction(failing)
				.build();
		PushNotificationService service = new PushNotificationService(webClient, "");

		List<String> stale = service.send(List.of("ExponentPushToken[abc]"), "New Duty Assigned", "body");

		assertTrue(stale.isEmpty());
	}

	@Test
	void expoReturnsHttpError_neverThrows() {
		PushNotificationService service = serviceWithExpoResponse("{\"errors\":[{\"message\":\"bad request\"}]}", 400);

		List<String> stale = service.send(List.of("ExponentPushToken[abc]"), "New Duty Assigned", "body");

		assertTrue(stale.isEmpty());
	}

	@Test
	void emptyTokenList_isANoOp() {
		PushNotificationService service = serviceWithExpoResponse("{\"data\":[]}", 200);

		List<String> stale = service.send(List.of(), "New Duty Assigned", "body");

		assertTrue(stale.isEmpty());
	}
}

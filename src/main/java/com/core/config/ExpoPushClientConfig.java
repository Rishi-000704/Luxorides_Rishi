package com.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/*
 * Phase 2C: iOS duty-assignment push is relayed through Expo's push service
 * (see PushNotificationService) rather than Firebase Admin, since a raw APNs
 * token can't be sent through Firebase Admin's FCM-only send() API. Unlike
 * googleMapsWebClient, Expo's push API requires no API key for a basic send
 * -- no credential wiring needed here.
 */
@Configuration
public class ExpoPushClientConfig {

	@Bean
	WebClient expoPushWebClient() {
		return WebClient.builder()
				.baseUrl("https://exp.host/--/api/v2/push")
				.defaultHeader("Content-Type", "application/json")
				.defaultHeader("Accept", "application/json")
				.build();
	}
}

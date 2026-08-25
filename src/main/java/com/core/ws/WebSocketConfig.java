package com.core.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

/*
 * Origin checking for WebSocket handshakes is separate from
 * SecurityConfiguration's corsConfigurationSource() bean -- that CORS bean
 * only governs normal Spring MVC requests, not WebSocketConfigurer
 * registrations, so allowed origins have to be set here explicitly.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

	private final BookingStatusWebSocketHandler bookingStatusWebSocketHandler;
	private final DutyPaymentWebSocketHandler dutyPaymentWebSocketHandler;
	private final DutyLocationWebSocketHandler dutyLocationWebSocketHandler;
	private final CustomerHandshakeInterceptor customerHandshakeInterceptor;
	private final DutyTokenHandshakeInterceptor dutyTokenHandshakeInterceptor;
	private final CustomerDutyLocationHandshakeInterceptor customerDutyLocationHandshakeInterceptor;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(bookingStatusWebSocketHandler, "/ws/bookings/*")
				.addInterceptors(customerHandshakeInterceptor)
				.setAllowedOriginPatterns("*");

		registry.addHandler(dutyPaymentWebSocketHandler, "/ws/duty-payment/*")
				.addInterceptors(dutyTokenHandshakeInterceptor)
				.setAllowedOriginPatterns("*");

		registry.addHandler(dutyLocationWebSocketHandler, "/ws/duty-location/*")
				.addInterceptors(customerDutyLocationHandshakeInterceptor)
				.setAllowedOriginPatterns("*");
	}
}

package com.core.ws;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.core.config.CorsOrigins;

import lombok.RequiredArgsConstructor;

/*
 * Origin checking for WebSocket handshakes is separate from
 * SecurityConfiguration's corsConfigurationSource() bean -- that CORS bean
 * only governs normal Spring MVC requests, not WebSocketConfigurer
 * registrations, so allowed origins have to be set here explicitly. Driven
 * by the same cors.allowed-origins property (see CorsOrigins), so the two
 * origin lists can't drift apart.
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

	@Value("${cors.allowed-origins}")
	private final String corsAllowedOrigins;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		List<String> allowedOrigins = CorsOrigins.parse(corsAllowedOrigins);
		String[] allowedOriginsArray = allowedOrigins.toArray(new String[0]);

		registry.addHandler(bookingStatusWebSocketHandler, "/ws/bookings/*")
				.addInterceptors(customerHandshakeInterceptor)
				.setAllowedOrigins(allowedOriginsArray);

		registry.addHandler(dutyPaymentWebSocketHandler, "/ws/duty-payment/*")
				.addInterceptors(dutyTokenHandshakeInterceptor)
				.setAllowedOrigins(allowedOriginsArray);

		registry.addHandler(dutyLocationWebSocketHandler, "/ws/duty-location/*")
				.addInterceptors(customerDutyLocationHandshakeInterceptor)
				.setAllowedOrigins(allowedOriginsArray);
	}
}

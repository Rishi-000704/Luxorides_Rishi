package com.core.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/*
 * Verifies WebSocketConfig wires the same cors.allowed-origins list (parsed
 * via CorsOrigins, see CorsOriginsTest) into all three handshake
 * registrations -- not Spring's own origin-enforcement machinery, which is
 * framework code and out of scope here; only that *our* config passes it
 * the right, exact (non-wildcard) origin list.
 */
class WebSocketConfigTest {

	private static final String ORIGINS_PROPERTY = "https://customer.test,https://fleetovo.test";
	private static final String[] EXPECTED_ORIGINS = { "https://customer.test", "https://fleetovo.test" };

	@Test
	void registerWebSocketHandlers_setsExactConfiguredOrigins_onAllThreeChannels() {
		BookingStatusWebSocketHandler bookingStatusWebSocketHandler = mock(BookingStatusWebSocketHandler.class);
		DutyPaymentWebSocketHandler dutyPaymentWebSocketHandler = mock(DutyPaymentWebSocketHandler.class);
		DutyLocationWebSocketHandler dutyLocationWebSocketHandler = mock(DutyLocationWebSocketHandler.class);
		CustomerHandshakeInterceptor customerHandshakeInterceptor = mock(CustomerHandshakeInterceptor.class);
		DutyTokenHandshakeInterceptor dutyTokenHandshakeInterceptor = mock(DutyTokenHandshakeInterceptor.class);
		CustomerDutyLocationHandshakeInterceptor customerDutyLocationHandshakeInterceptor =
				mock(CustomerDutyLocationHandshakeInterceptor.class);

		WebSocketConfig config = new WebSocketConfig(
				bookingStatusWebSocketHandler,
				dutyPaymentWebSocketHandler,
				dutyLocationWebSocketHandler,
				customerHandshakeInterceptor,
				dutyTokenHandshakeInterceptor,
				customerDutyLocationHandshakeInterceptor,
				ORIGINS_PROPERTY);

		WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
		WebSocketHandlerRegistration bookingsRegistration = mockChainableRegistration();
		WebSocketHandlerRegistration paymentRegistration = mockChainableRegistration();
		WebSocketHandlerRegistration locationRegistration = mockChainableRegistration();

		when(registry.addHandler(any(WebSocketHandler.class), org.mockito.ArgumentMatchers.eq("/ws/bookings/*")))
				.thenReturn(bookingsRegistration);
		when(registry.addHandler(any(WebSocketHandler.class), org.mockito.ArgumentMatchers.eq("/ws/duty-payment/*")))
				.thenReturn(paymentRegistration);
		when(registry.addHandler(any(WebSocketHandler.class), org.mockito.ArgumentMatchers.eq("/ws/duty-location/*")))
				.thenReturn(locationRegistration);

		config.registerWebSocketHandlers(registry);

		verify(bookingsRegistration).setAllowedOrigins(EXPECTED_ORIGINS);
		verify(paymentRegistration).setAllowedOrigins(EXPECTED_ORIGINS);
		verify(locationRegistration).setAllowedOrigins(EXPECTED_ORIGINS);
	}

	private WebSocketHandlerRegistration mockChainableRegistration() {
		WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
		when(registration.addInterceptors(org.mockito.ArgumentMatchers.<HandshakeInterceptor>any()))
				.thenReturn(registration);
		when(registration.setAllowedOrigins(org.mockito.ArgumentMatchers.<String>any()))
				.thenReturn(registration);
		return registration;
	}
}

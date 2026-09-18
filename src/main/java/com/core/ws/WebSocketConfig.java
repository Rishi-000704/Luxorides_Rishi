package com.core.ws;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.core.config.CorsOrigins;

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
public class WebSocketConfig implements WebSocketConfigurer {

	private final BookingStatusWebSocketHandler bookingStatusWebSocketHandler;
	private final DutyPaymentWebSocketHandler dutyPaymentWebSocketHandler;
	private final DutyLocationWebSocketHandler dutyLocationWebSocketHandler;
	private final CustomerHandshakeInterceptor customerHandshakeInterceptor;
	private final DutyTokenHandshakeInterceptor dutyTokenHandshakeInterceptor;
	private final CustomerDutyLocationHandshakeInterceptor customerDutyLocationHandshakeInterceptor;
	private final EmployeeBookingHandshakeInterceptor employeeBookingHandshakeInterceptor;
	private final String corsAllowedOrigins;

	// Explicit constructor instead of @RequiredArgsConstructor: Lombok doesn't
	// copy @Value onto a generated constructor parameter (no lombok.config
	// copyableAnnotations entry in this repo), which made Spring try to
	// autowire a bare String bean by type and fail. @Value belongs on the
	// constructor parameter itself here, so property resolution works;
	// signature/order is unchanged, so existing callers (WebSocketConfigTest)
	// are unaffected.
	public WebSocketConfig(
			BookingStatusWebSocketHandler bookingStatusWebSocketHandler,
			DutyPaymentWebSocketHandler dutyPaymentWebSocketHandler,
			DutyLocationWebSocketHandler dutyLocationWebSocketHandler,
			CustomerHandshakeInterceptor customerHandshakeInterceptor,
			DutyTokenHandshakeInterceptor dutyTokenHandshakeInterceptor,
			CustomerDutyLocationHandshakeInterceptor customerDutyLocationHandshakeInterceptor,
			EmployeeBookingHandshakeInterceptor employeeBookingHandshakeInterceptor,
			@Value("${cors.allowed-origins}") String corsAllowedOrigins) {
		this.bookingStatusWebSocketHandler = bookingStatusWebSocketHandler;
		this.dutyPaymentWebSocketHandler = dutyPaymentWebSocketHandler;
		this.dutyLocationWebSocketHandler = dutyLocationWebSocketHandler;
		this.customerHandshakeInterceptor = customerHandshakeInterceptor;
		this.dutyTokenHandshakeInterceptor = dutyTokenHandshakeInterceptor;
		this.customerDutyLocationHandshakeInterceptor = customerDutyLocationHandshakeInterceptor;
		this.employeeBookingHandshakeInterceptor = employeeBookingHandshakeInterceptor;
		this.corsAllowedOrigins = corsAllowedOrigins;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		List<String> allowedOrigins = CorsOrigins.parse(corsAllowedOrigins);
		String[] allowedOriginsArray = allowedOrigins.toArray(new String[0]);

		registry.addHandler(bookingStatusWebSocketHandler, "/ws/bookings/*")
				.addInterceptors(customerHandshakeInterceptor)
				.setAllowedOrigins(allowedOriginsArray);

		// Same handler + same BookingChannelRegistry as the customer channel
		// above -- an ops session registers under the same bookingId key, so
		// it receives the identical broadcast signal a subscribed customer
		// would, with no separate registry/broadcast path to keep in sync.
		registry.addHandler(bookingStatusWebSocketHandler, "/ws/ops/bookings/*")
				.addInterceptors(employeeBookingHandshakeInterceptor)
				.setAllowedOrigins(allowedOriginsArray);

		registry.addHandler(dutyPaymentWebSocketHandler, "/ws/duty-payment/*")
				.addInterceptors(dutyTokenHandshakeInterceptor)
				.setAllowedOrigins(allowedOriginsArray);

		registry.addHandler(dutyLocationWebSocketHandler, "/ws/duty-location/*")
				.addInterceptors(customerDutyLocationHandshakeInterceptor)
				.setAllowedOrigins(allowedOriginsArray);
	}
}

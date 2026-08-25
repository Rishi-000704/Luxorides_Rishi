package com.core.ws;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.core.models.User;
import com.core.services.BookingService;
import com.core.services.JwtService;

import lombok.RequiredArgsConstructor;

/*
 * Native browser WebSocket can't set an Authorization header, so the JWT
 * travels as a query param instead (?token=...) and is validated here,
 * once, at handshake time -- same JwtService calls JwtAuthenticationFilter
 * uses for every other authenticated request. Booking ownership is checked
 * with the same org-scoped rule ClientBookingController.getBookingDetail
 * already relies on (BookingService.getBooking throws if the booking isn't
 * in this JWT's org) -- not stricter, not looser.
 */
@Component
@RequiredArgsConstructor
public class CustomerHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final BookingService bookingService;

	@Override
	public boolean beforeHandshake(
			@NonNull ServerHttpRequest request,
			@NonNull ServerHttpResponse response,
			@NonNull WebSocketHandler wsHandler,
			@NonNull Map<String, Object> attributes) {

		String token = extractQueryParam(request.getURI(), "token");
		String bookingId = extractLastPathSegment(request.getURI());

		if (token == null || bookingId == null) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}

		try {
			String userId = jwtService.extractUserId(token);
			User user = (User) userDetailsService.loadUserByUsername(userId);

			if (!jwtService.isTokenValid(token, user)) {
				response.setStatusCode(HttpStatus.UNAUTHORIZED);
				return false;
			}

			bookingService.getBooking(bookingId, user.getOrgId());

			attributes.put(BookingStatusWebSocketHandler.BOOKING_ID_ATTRIBUTE, bookingId);
			return true;
		} catch (Exception ex) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterHandshake(
			@NonNull ServerHttpRequest request,
			@NonNull ServerHttpResponse response,
			@NonNull WebSocketHandler wsHandler,
			Exception exception) {
	}

	private String extractQueryParam(URI uri, String name) {
		return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
	}

	private String extractLastPathSegment(URI uri) {
		String path = uri.getPath();

		if (path == null || path.isBlank()) {
			return null;
		}

		String[] segments = path.split("/");
		return segments.length == 0 ? null : segments[segments.length - 1];
	}
}

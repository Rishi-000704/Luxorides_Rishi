package com.core.ws;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.core.models.Booking;
import com.core.models.Client;
import com.core.models.User;
import com.core.services.BookingService;
import com.core.services.ClientService;
import com.core.services.JwtService;

import lombok.RequiredArgsConstructor;

/*
 * Native browser WebSocket can't set an Authorization header, so the JWT
 * travels as a query param instead (?token=...) and is validated here,
 * once, at handshake time -- same JwtService calls JwtAuthenticationFilter
 * uses for every other authenticated request.
 *
 * P0 IDOR fix -- this used to only check that the booking exists in this
 * JWT's org (same gap ClientBookingController.getBookingDetail had), which
 * let any authenticated client in the same org subscribe to another
 * client's live booking-status stream by guessing a bookingId. Now also
 * requires the requesting client to own the booking, same check
 * getOwnedBooking enforces on the REST fallback.
 */
@Component
@RequiredArgsConstructor
public class CustomerHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final BookingService bookingService;
	private final ClientService clientService;

	// P0 -- a disabled/revoked account must not be able to open a NEW
	// WebSocket connection with an old JWT either. Same reused Spring
	// Security check as JwtAuthenticationFilter, not a bespoke one.
	private static final UserDetailsChecker ACCOUNT_STATUS_CHECKER = new AccountStatusUserDetailsChecker();

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

			// Fresh DB read above (UserDetailsService does a plain findById,
			// no caching), so this is today's authoritative account status,
			// not whatever was true when this JWT was issued.
			ACCOUNT_STATUS_CHECKER.check(user);

			Booking booking = bookingService.getBooking(bookingId, user.getOrgId());
			Client client = clientService.findByUserId(user.getId());

			if (!client.getId().equals(booking.getClientId())) {
				response.setStatusCode(HttpStatus.UNAUTHORIZED);
				return false;
			}

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

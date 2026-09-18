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

import com.core.models.User;
import com.core.models.enums.AccountType;
import com.core.models.enums.Authority;
import com.core.services.BookingService;
import com.core.services.JwtService;

import lombok.RequiredArgsConstructor;

/*
 * Ops (employee) counterpart to CustomerHandshakeInterceptor -- reuses the
 * exact same channel (BookingStatusWebSocketHandler / BookingChannelRegistry,
 * same bare {bookingId, event} signal broadcast by RealtimeBookingBroadcastListener),
 * just under a different handshake path (/ws/ops/bookings/{bookingId}, see
 * WebSocketConfig) with a different ownership rule: an employee doesn't own
 * a booking the way a client does, so this checks org membership + the
 * BOOKING_VIEW authority instead -- the exact same rule
 * EmployeeBookingController#getProfile already enforces on the REST fetch
 * this channel's signal triggers on the ops side.
 */
@Component
@RequiredArgsConstructor
public class EmployeeBookingHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final BookingService bookingService;

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

			// Fresh DB read above (UserDetailsService does a plain findById, no
			// caching), so this is today's authoritative account status, not
			// whatever was true when this JWT was issued.
			ACCOUNT_STATUS_CHECKER.check(user);

			if (user.getAccountType() != AccountType.EMPLOYEE
					|| !user.getAuthorityList().contains(Authority.BOOKING_VIEW)) {
				response.setStatusCode(HttpStatus.UNAUTHORIZED);
				return false;
			}

			// Org-scoped lookup -- throws for a booking that doesn't exist in
			// this employee's org (cross-org access), caught below as 401, same
			// as the REST endpoint's own org-scoped query.
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

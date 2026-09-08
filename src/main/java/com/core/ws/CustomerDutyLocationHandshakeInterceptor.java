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

import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.User;
import com.core.repositories.BookingEntryRepository;
import com.core.services.ClientService;
import com.core.services.JwtService;

import lombok.RequiredArgsConstructor;

/*
 * Same auth shape as CustomerHandshakeInterceptor (JWT-in-query-param, since
 * native WebSocket can't set headers) -- this channel is read by the
 * customer app, keyed by dutyId rather than bookingId since a booking can
 * have multiple entries/drivers.
 *
 * P0 IDOR fix -- this used to only check the duty exists in this JWT's org,
 * which let any authenticated client in the same org subscribe to another
 * client's live vehicle GPS stream by guessing a dutyId. findByDutyIdAndOrgId
 * JOIN FETCHes the booking, so entry.getBooking().getClientId() is safe to
 * read here despite that association being LAZY (already fully loaded by
 * this same query, not a proxy needing a live session).
 */
@Component
@RequiredArgsConstructor
public class CustomerDutyLocationHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final BookingEntryRepository bookingEntryRepository;
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
		String dutyId = extractLastPathSegment(request.getURI());

		if (token == null || dutyId == null) {
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

			BookingEntry entry = bookingEntryRepository.findByDutyIdAndOrgId(dutyId, user.getOrgId())
					.orElseThrow();

			Client client = clientService.findByUserId(user.getId());

			if (!client.getId().equals(entry.getBooking().getClientId())) {
				response.setStatusCode(HttpStatus.UNAUTHORIZED);
				return false;
			}

			attributes.put(DutyLocationWebSocketHandler.DUTY_ID_ATTRIBUTE, dutyId);
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

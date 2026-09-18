package com.core.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.WebSocketHandler;

import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Booking;
import com.core.models.User;
import com.core.models.enums.AccountType;
import com.core.models.enums.Authority;
import com.core.services.BookingService;
import com.core.services.JwtService;

/*
 * Ops counterpart to CustomerHandshakeInterceptorTest -- proves the
 * org+authority rule this interceptor enforces (EMPLOYEE account type,
 * BOOKING_VIEW authority, booking exists in the caller's own org) rather
 * than the customer channel's ownership rule, which doesn't apply to an
 * employee at all.
 */
class EmployeeBookingHandshakeInterceptorTest {

	private static final String BOOKING_ID = "booking-1";
	private static final String ORG_ID = "org-1";
	private static final String OTHER_ORG_ID = "org-2";
	private static final String TOKEN = "jwt-token";
	private static final String USER_ID = "user-1";

	private JwtService jwtService;
	private UserDetailsService userDetailsService;
	private BookingService bookingService;
	private EmployeeBookingHandshakeInterceptor interceptor;
	private ServerHttpResponse response;

	@BeforeEach
	void setUp() {
		jwtService = mock(JwtService.class);
		userDetailsService = mock(UserDetailsService.class);
		bookingService = mock(BookingService.class);
		interceptor = new EmployeeBookingHandshakeInterceptor(jwtService, userDetailsService, bookingService);
		response = mock(ServerHttpResponse.class);
	}

	private ServerHttpRequest requestFor(String bookingId) {
		ServerHttpRequest request = mock(ServerHttpRequest.class);
		when(request.getURI())
				.thenReturn(URI.create("wss://api.test/ws/ops/bookings/" + bookingId + "?token=" + TOKEN));
		return request;
	}

	private User employeeUser(List<Authority> authorities) {
		User user = new User();
		user.setId(USER_ID);
		user.setOrgId(ORG_ID);
		user.setAccountType(AccountType.EMPLOYEE);
		user.setAuthorities(authorities);
		return user;
	}

	private Booking bookingInOrg(String orgId) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(orgId);
		return booking;
	}

	private void stubValidToken(User user) {
		when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
		when(userDetailsService.loadUserByUsername(USER_ID)).thenReturn(user);
		when(jwtService.isTokenValid(TOKEN, user)).thenReturn(true);
	}

	@Test
	void beforeHandshake_employeeWithBookingView_inOwnOrg_succeeds() {
		User user = employeeUser(List.of(Authority.BOOKING_VIEW));
		stubValidToken(user);
		when(bookingService.getBooking(BOOKING_ID, ORG_ID)).thenReturn(bookingInOrg(ORG_ID));

		Map<String, Object> attributes = new HashMap<>();
		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), attributes);

		assertTrue(result);
		assertTrue(attributes.containsKey(BookingStatusWebSocketHandler.BOOKING_ID_ATTRIBUTE));
	}

	@Test
	void beforeHandshake_employeeWithoutBookingViewAuthority_isRejected() {
		User user = employeeUser(List.of(Authority.DRIVER_VIEW));
		stubValidToken(user);

		Map<String, Object> attributes = new HashMap<>();
		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), attributes);

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		assertFalse(attributes.containsKey(BookingStatusWebSocketHandler.BOOKING_ID_ATTRIBUTE));
	}

	@Test
	void beforeHandshake_nonEmployeeAccountType_isRejected_evenWithBookingViewAuthority() {
		User user = employeeUser(List.of(Authority.BOOKING_VIEW));
		user.setAccountType(AccountType.CLIENT);
		stubValidToken(user);

		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), new HashMap<>());

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}

	// P0-equivalent org isolation -- a booking that exists but belongs to a
	// different org must never be reachable, mirroring
	// EmployeeBookingController#getProfile's own org-scoped lookup.
	@Test
	void beforeHandshake_bookingBelongsToAnotherOrg_isRejected() {
		User user = employeeUser(List.of(Authority.BOOKING_VIEW));
		stubValidToken(user);
		when(bookingService.getBooking(BOOKING_ID, ORG_ID))
				.thenThrow(new NotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking not found with this booking Id."));

		Map<String, Object> attributes = new HashMap<>();
		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), attributes);

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		assertFalse(attributes.containsKey(BookingStatusWebSocketHandler.BOOKING_ID_ATTRIBUTE));
	}

	@Test
	void beforeHandshake_invalidToken_isRejected() {
		when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
		User user = employeeUser(List.of(Authority.BOOKING_VIEW));
		when(userDetailsService.loadUserByUsername(USER_ID)).thenReturn(user);
		when(jwtService.isTokenValid(TOKEN, user)).thenReturn(false);

		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), new HashMap<>());

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void beforeHandshake_disabledAccount_isRejected_evenWithAnOtherwiseValidToken() {
		User user = employeeUser(List.of(Authority.BOOKING_VIEW));
		user.setEnabled(false);
		stubValidToken(user);

		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), new HashMap<>());

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void beforeHandshake_missingTokenOrBookingId_isRejected() {
		ServerHttpRequest noTokenRequest = mock(ServerHttpRequest.class);
		when(noTokenRequest.getURI()).thenReturn(URI.create("wss://api.test/ws/ops/bookings/" + BOOKING_ID));

		boolean result = interceptor.beforeHandshake(
				noTokenRequest, response, mock(WebSocketHandler.class), new HashMap<>());

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}
}

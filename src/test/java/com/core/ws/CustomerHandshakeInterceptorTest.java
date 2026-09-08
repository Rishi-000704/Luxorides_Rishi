package com.core.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.WebSocketHandler;

import com.core.models.Booking;
import com.core.models.Client;
import com.core.models.User;
import com.core.models.enums.AccountType;
import com.core.services.BookingService;
import com.core.services.ClientService;
import com.core.services.JwtService;

/*
 * P0 IDOR fix -- this handshake used to only check that the requested
 * booking exists in the caller's org, which let any authenticated client in
 * the same org subscribe to another client's live booking-status stream by
 * guessing a bookingId. Now also requires the requesting client to own the
 * booking, mirroring ClientBookingController.getBookingDetail's fix.
 */
class CustomerHandshakeInterceptorTest {

	private static final String BOOKING_ID = "booking-1";
	private static final String ORG_ID = "org-1";
	private static final String OWNING_CLIENT_ID = "client-owner";
	private static final String OTHER_CLIENT_ID = "client-other";
	private static final String TOKEN = "jwt-token";
	private static final String USER_ID = "user-1";

	private JwtService jwtService;
	private UserDetailsService userDetailsService;
	private BookingService bookingService;
	private ClientService clientService;
	private CustomerHandshakeInterceptor interceptor;
	private ServerHttpResponse response;

	@BeforeEach
	void setUp() {
		jwtService = mock(JwtService.class);
		userDetailsService = mock(UserDetailsService.class);
		bookingService = mock(BookingService.class);
		clientService = mock(ClientService.class);
		interceptor = new CustomerHandshakeInterceptor(jwtService, userDetailsService, bookingService, clientService);
		response = mock(ServerHttpResponse.class);
	}

	private ServerHttpRequest requestFor(String bookingId) {
		ServerHttpRequest request = mock(ServerHttpRequest.class);
		when(request.getURI()).thenReturn(URI.create("wss://api.test/ws/bookings/" + bookingId + "?token=" + TOKEN));
		return request;
	}

	private User authenticatedUser() {
		User user = new User();
		user.setId(USER_ID);
		user.setOrgId(ORG_ID);
		user.setAccountType(AccountType.CLIENT);
		return user;
	}

	private Booking bookingOwnedBy(String clientId) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setClientId(clientId);
		return booking;
	}

	private void stubValidToken(User user) {
		when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
		when(userDetailsService.loadUserByUsername(USER_ID)).thenReturn(user);
		when(jwtService.isTokenValid(TOKEN, user)).thenReturn(true);
	}

	@Test
	void beforeHandshake_ownerConnectsToTheirOwnBooking_succeeds() {
		User user = authenticatedUser();
		stubValidToken(user);
		when(bookingService.getBooking(BOOKING_ID, ORG_ID)).thenReturn(bookingOwnedBy(OWNING_CLIENT_ID));
		Client client = new Client();
		client.setId(OWNING_CLIENT_ID);
		when(clientService.findByUserId(USER_ID)).thenReturn(client);

		Map<String, Object> attributes = new HashMap<>();
		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), attributes);

		assertTrue(result);
		assertTrue(attributes.containsKey(BookingStatusWebSocketHandler.BOOKING_ID_ATTRIBUTE));
	}

	@Test
	void beforeHandshake_anotherClientInSameOrgAttemptsToConnect_isRejected() {
		User user = authenticatedUser();
		stubValidToken(user);
		when(bookingService.getBooking(BOOKING_ID, ORG_ID)).thenReturn(bookingOwnedBy(OWNING_CLIENT_ID));
		Client client = new Client();
		client.setId(OTHER_CLIENT_ID);
		when(clientService.findByUserId(USER_ID)).thenReturn(client);

		Map<String, Object> attributes = new HashMap<>();
		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), attributes);

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		assertFalse(attributes.containsKey(BookingStatusWebSocketHandler.BOOKING_ID_ATTRIBUTE));
	}

	@Test
	void beforeHandshake_invalidToken_isRejected_withoutLeakingWhetherBookingExists() {
		when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
		User user = authenticatedUser();
		when(userDetailsService.loadUserByUsername(USER_ID)).thenReturn(user);
		when(jwtService.isTokenValid(TOKEN, user)).thenReturn(false);

		boolean result = interceptor.beforeHandshake(
				requestFor(BOOKING_ID), response, mock(WebSocketHandler.class), new HashMap<>());

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}
}

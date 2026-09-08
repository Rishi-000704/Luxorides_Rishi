package com.core.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.WebSocketHandler;

import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.User;
import com.core.models.enums.AccountType;
import com.core.repositories.BookingEntryRepository;
import com.core.services.ClientService;
import com.core.services.JwtService;

/*
 * P0 IDOR fix -- this handshake used to only check that the requested duty
 * exists in the caller's org, which let any authenticated client in the
 * same org subscribe to another client's live vehicle GPS stream by
 * guessing a dutyId. Now also requires the requesting client to own the
 * booking the duty belongs to.
 */
class CustomerDutyLocationHandshakeInterceptorTest {

	private static final String DUTY_ID = "duty-1";
	private static final String ORG_ID = "org-1";
	private static final String OWNING_CLIENT_ID = "client-owner";
	private static final String OTHER_CLIENT_ID = "client-other";
	private static final String TOKEN = "jwt-token";
	private static final String USER_ID = "user-1";

	private JwtService jwtService;
	private UserDetailsService userDetailsService;
	private BookingEntryRepository bookingEntryRepository;
	private ClientService clientService;
	private CustomerDutyLocationHandshakeInterceptor interceptor;
	private ServerHttpResponse response;

	@BeforeEach
	void setUp() {
		jwtService = mock(JwtService.class);
		userDetailsService = mock(UserDetailsService.class);
		bookingEntryRepository = mock(BookingEntryRepository.class);
		clientService = mock(ClientService.class);
		interceptor = new CustomerDutyLocationHandshakeInterceptor(
				jwtService, userDetailsService, bookingEntryRepository, clientService);
		response = mock(ServerHttpResponse.class);
	}

	private ServerHttpRequest requestFor(String dutyId) {
		ServerHttpRequest request = mock(ServerHttpRequest.class);
		when(request.getURI()).thenReturn(URI.create("wss://api.test/ws/duty-location/" + dutyId + "?token=" + TOKEN));
		return request;
	}

	private User authenticatedUser() {
		User user = new User();
		user.setId(USER_ID);
		user.setOrgId(ORG_ID);
		user.setAccountType(AccountType.CLIENT);
		return user;
	}

	private BookingEntry entryForBookingOwnedBy(String clientId) {
		Booking booking = new Booking();
		booking.setBookingId("booking-1");
		booking.setOrgId(ORG_ID);
		booking.setClientId(clientId);

		BookingEntry entry = new BookingEntry();
		entry.setDutyId(DUTY_ID);
		entry.setBooking(booking);
		return entry;
	}

	private void stubValidToken(User user) {
		when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
		when(userDetailsService.loadUserByUsername(USER_ID)).thenReturn(user);
		when(jwtService.isTokenValid(TOKEN, user)).thenReturn(true);
	}

	@Test
	void beforeHandshake_ownerConnectsToTheirOwnDutyLocation_succeeds() {
		stubValidToken(authenticatedUser());
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID))
				.thenReturn(Optional.of(entryForBookingOwnedBy(OWNING_CLIENT_ID)));
		Client client = new Client();
		client.setId(OWNING_CLIENT_ID);
		when(clientService.findByUserId(USER_ID)).thenReturn(client);

		Map<String, Object> attributes = new HashMap<>();
		boolean result = interceptor.beforeHandshake(
				requestFor(DUTY_ID), response, mock(WebSocketHandler.class), attributes);

		assertTrue(result);
		assertTrue(attributes.containsKey(DutyLocationWebSocketHandler.DUTY_ID_ATTRIBUTE));
	}

	@Test
	void beforeHandshake_anotherClientInSameOrgAttemptsToConnect_isRejected_neverStreamsTheOtherClientsGps() {
		stubValidToken(authenticatedUser());
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID))
				.thenReturn(Optional.of(entryForBookingOwnedBy(OWNING_CLIENT_ID)));
		Client client = new Client();
		client.setId(OTHER_CLIENT_ID);
		when(clientService.findByUserId(USER_ID)).thenReturn(client);

		Map<String, Object> attributes = new HashMap<>();
		boolean result = interceptor.beforeHandshake(
				requestFor(DUTY_ID), response, mock(WebSocketHandler.class), attributes);

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
		assertFalse(attributes.containsKey(DutyLocationWebSocketHandler.DUTY_ID_ATTRIBUTE));
	}

	@Test
	void beforeHandshake_dutyDoesNotExistInOrg_isRejected() {
		stubValidToken(authenticatedUser());
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.empty());

		boolean result = interceptor.beforeHandshake(
				requestFor(DUTY_ID), response, mock(WebSocketHandler.class), new HashMap<>());

		assertFalse(result);
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}
}

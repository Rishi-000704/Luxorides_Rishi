package com.core.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.core.exception.NotFoundException;
import com.core.models.User;
import com.core.models.enums.AccountType;
import com.core.services.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * P0 -- this filter is the single choke point every Bearer-JWT request goes
 * through (Customer/Employee/Driver all share it, see User implements
 * UserDetails with a single AccountType-discriminated table). Before this
 * fix, isTokenValid only checked the JWT's own subject/expiry, never
 * whether the account is still enabled -- an already-issued JWT (up to
 * ~30 days) kept working after the account was disabled. userDetailsService
 * here is always a fresh, uncached DB read (ApplicationConfiguration#userDetailsService),
 * so simply checking the freshly-loaded UserDetails' status on every request
 * closes the gap without any token-version/session/blacklist machinery.
 */
class JwtAuthenticationFilterTest {

	private static final String USER_ID = "user-1";
	private static final String JWT = "jwt-token-value";

	private JwtService jwtService;
	private UserDetailsService userDetailsService;
	private HandlerExceptionResolver handlerExceptionResolver;
	private JwtAuthenticationFilter filter;
	private HttpServletRequest request;
	private HttpServletResponse response;
	private FilterChain filterChain;

	@BeforeEach
	void setUp() {
		jwtService = mock(JwtService.class);
		userDetailsService = mock(UserDetailsService.class);
		handlerExceptionResolver = mock(HandlerExceptionResolver.class);
		filter = new JwtAuthenticationFilter(jwtService, userDetailsService, handlerExceptionResolver);

		request = mock(HttpServletRequest.class);
		response = mock(HttpServletResponse.class);
		filterChain = mock(FilterChain.class);

		when(request.getMethod()).thenReturn("GET");
		when(request.getHeader("Authorization")).thenReturn("Bearer " + JWT);

		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private User user(boolean enabled) {
		User u = new User();
		u.setId(USER_ID);
		u.setOrgId("org-1");
		u.setAccountType(AccountType.DRIVER);
		u.setEnabled(enabled);
		return u;
	}

	private void stubValidJwtFor(User user) {
		when(jwtService.extractUserId(JWT)).thenReturn(USER_ID);
		when(userDetailsService.loadUserByUsername(USER_ID)).thenReturn(user);
		when(jwtService.isTokenValid(JWT, user)).thenReturn(true);
	}

	@Test
	void enabledAccount_validJwt_authenticationSucceeds() throws Exception {
		stubValidJwtFor(user(true));

		filter.doFilterInternal(request, response, filterChain);

		assertNotNull(SecurityContextHolder.getContext().getAuthentication());
		verify(filterChain, times(1)).doFilter(request, response);
		verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());
	}

	@Test
	void disabledAccount_otherwiseValidJwt_authenticationRejected() throws Exception {
		stubValidJwtFor(user(false));

		filter.doFilterInternal(request, response, filterChain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		verify(filterChain, never()).doFilter(any(), any());
		verify(handlerExceptionResolver, times(1))
				.resolveException(eq(request), eq(response), isNull(), any(DisabledException.class));
	}

	// The exact P0 scenario -- same JWT, same request shape, only the
	// account's CURRENT status changed between the two requests (simulating
	// an admin disabling the account after the token was already issued).
	@Test
	void accountDisabledAfterJwtIssuance_subsequentRequestIsRejected() throws Exception {
		User backingUser = user(true);
		stubValidJwtFor(backingUser);

		filter.doFilterInternal(request, response, filterChain);
		assertNotNull(SecurityContextHolder.getContext().getAuthentication());

		SecurityContextHolder.clearContext();
		backingUser.setEnabled(false);

		filter.doFilterInternal(request, response, filterChain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		verify(handlerExceptionResolver, times(1))
				.resolveException(eq(request), eq(response), isNull(), any(DisabledException.class));
	}

	@Test
	void reEnabledAccount_followsCurrentAuthoritativeStatus() throws Exception {
		User backingUser = user(false);
		stubValidJwtFor(backingUser);

		filter.doFilterInternal(request, response, filterChain);
		assertNull(SecurityContextHolder.getContext().getAuthentication());

		SecurityContextHolder.clearContext();
		backingUser.setEnabled(true);

		filter.doFilterInternal(request, response, filterChain);

		assertNotNull(SecurityContextHolder.getContext().getAuthentication());
		verify(filterChain, times(1)).doFilter(request, response);
	}

	@Test
	void expiredJwt_remainsRejected() throws Exception {
		User u = user(true);
		when(jwtService.extractUserId(JWT)).thenReturn(USER_ID);
		when(userDetailsService.loadUserByUsername(USER_ID)).thenReturn(u);
		when(jwtService.isTokenValid(JWT, u)).thenReturn(false);

		filter.doFilterInternal(request, response, filterChain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		// Not authenticated, but the chain still proceeds -- downstream
		// authorizeHttpRequests().anyRequest().authenticated() is what
		// actually rejects the (now-unauthenticated) request.
		verify(filterChain, times(1)).doFilter(request, response);
	}

	@Test
	void tamperedOrInvalidJwt_remainsRejected() throws Exception {
		when(jwtService.extractUserId(JWT)).thenThrow(new io.jsonwebtoken.security.SignatureException("bad signature"));

		filter.doFilterInternal(request, response, filterChain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		verify(filterChain, never()).doFilter(any(), any());
		verify(handlerExceptionResolver, times(1)).resolveException(eq(request), eq(response), isNull(), any());
	}

	@Test
	void unknownAccount_remainsRejected() throws Exception {
		when(jwtService.extractUserId(JWT)).thenReturn(USER_ID);
		when(userDetailsService.loadUserByUsername(USER_ID))
				.thenThrow(new NotFoundException(com.core.exception.ErrorCode.USER_NOT_FOUND, "User not found."));

		filter.doFilterInternal(request, response, filterChain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		verify(filterChain, never()).doFilter(any(), any());
		verify(handlerExceptionResolver, times(1)).resolveException(eq(request), eq(response), isNull(), any(NotFoundException.class));
	}

	@Test
	void noAuthorizationHeader_passesThroughUnauthenticated() throws Exception {
		when(request.getHeader("Authorization")).thenReturn(null);

		filter.doFilterInternal(request, response, filterChain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		verify(filterChain, times(1)).doFilter(request, response);
	}
}

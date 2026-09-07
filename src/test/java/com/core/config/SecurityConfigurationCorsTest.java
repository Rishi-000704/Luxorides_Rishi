package com.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/*
 * Exercises the real Spring CorsFilter (the same mechanism
 * SecurityFilterChain's .cors(...) wires up) against the CorsConfigurationSource
 * this bean produces -- no Spring context boot, no DB, consistent with the
 * rest of this test suite. Test-specific origins only (https://*.test),
 * never a real production domain.
 */
class SecurityConfigurationCorsTest {

	private static final String ALLOWED_ORIGIN = "https://customer.test";
	private static final String OTHER_ALLOWED_ORIGIN = "https://fleetovo.test";
	private static final String DISALLOWED_ORIGIN = "https://evil.example";

	private CorsFilter corsFilter;

	@BeforeEach
	void setUp() {
		JwtAuthenticationFilter jwtAuthenticationFilter = mock(JwtAuthenticationFilter.class);
		AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);

		SecurityConfiguration securityConfiguration = new SecurityConfiguration(
				jwtAuthenticationFilter, authenticationProvider,
				ALLOWED_ORIGIN + "," + OTHER_ALLOWED_ORIGIN);

		CorsConfigurationSource source = securityConfiguration.corsConfigurationSource();
		corsFilter = new CorsFilter(source);
	}

	@Test
	void preflight_allowedOrigin_succeedsAndReflectsOrigin() throws Exception {
		MockHttpServletRequest request = preflightRequest(ALLOWED_ORIGIN);
		MockHttpServletResponse response = new MockHttpServletResponse();

		corsFilter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertEquals(ALLOWED_ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
	}

	@Test
	void preflight_secondConfiguredOrigin_alsoSucceeds() throws Exception {
		MockHttpServletRequest request = preflightRequest(OTHER_ALLOWED_ORIGIN);
		MockHttpServletResponse response = new MockHttpServletResponse();

		corsFilter.doFilter(request, response, new MockFilterChain());

		assertEquals(OTHER_ALLOWED_ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
	}

	@Test
	void preflight_disallowedOrigin_rejectedWithNoAllowOriginHeader() throws Exception {
		MockHttpServletRequest request = preflightRequest(DISALLOWED_ORIGIN);
		MockHttpServletResponse response = new MockHttpServletResponse();

		corsFilter.doFilter(request, response, new MockFilterChain());

		assertNull(response.getHeader("Access-Control-Allow-Origin"));
		assertEquals(403, response.getStatus());
	}

	@Test
	void preflight_allowedOrigin_allowsAuthorizationHeader() throws Exception {
		MockHttpServletRequest request = preflightRequest(ALLOWED_ORIGIN);
		request.addHeader("Access-Control-Request-Headers", "Authorization");
		MockHttpServletResponse response = new MockHttpServletResponse();

		corsFilter.doFilter(request, response, new MockFilterChain());

		String allowHeaders = response.getHeader("Access-Control-Allow-Headers");
		assertNotNull(allowHeaders);
		assertTrue(allowHeaders.toLowerCase().contains("authorization"),
				"Authorization header must remain allowed cross-origin for the Bearer-JWT frontends");
	}

	@Test
	void preflight_allowedOrigin_allowsAllRequiredMethods() throws Exception {
		for (String method : new String[] { "GET", "POST", "PUT", "DELETE" }) {
			MockHttpServletRequest request = preflightRequest(ALLOWED_ORIGIN);
			request.addHeader("Access-Control-Request-Method", method);
			MockHttpServletResponse response = new MockHttpServletResponse();

			corsFilter.doFilter(request, response, new MockFilterChain());

			String allowMethods = response.getHeader("Access-Control-Allow-Methods");
			assertNotNull(allowMethods, "missing Access-Control-Allow-Methods for " + method);
			assertTrue(allowMethods.contains(method));
		}
	}

	/*
	 * Customer/Fleetovo authenticate with a Bearer JWT, never a cookie (see
	 * SecurityConfiguration's comment on setAllowCredentials(false)) -- so
	 * the CORS response correctly never advertises credentialed access, and
	 * that's fine: a Bearer Authorization header isn't a "credential" in the
	 * fetch/XHR sense CORS gates behind Access-Control-Allow-Credentials.
	 */
	@Test
	void preflight_neverAdvertisesAllowCredentials() throws Exception {
		MockHttpServletRequest request = preflightRequest(ALLOWED_ORIGIN);
		MockHttpServletResponse response = new MockHttpServletResponse();

		corsFilter.doFilter(request, response, new MockFilterChain());

		assertNull(response.getHeader("Access-Control-Allow-Credentials"));
	}

	private MockHttpServletRequest preflightRequest(String origin) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("OPTIONS");
		request.addHeader("Origin", origin);
		request.addHeader("Access-Control-Request-Method", "GET");
		request.setRequestURI("/booking/employee/estimates");
		return request;
	}
}

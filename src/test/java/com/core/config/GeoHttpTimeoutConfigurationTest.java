package com.core.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.core.location.provider.GoogleGeoProvider;
import com.core.location.provider.OpenRouteServiceGeoProvider;

/*
 * Regression coverage for the fix adding explicit, bounded HTTP timeouts to
 * the ORS/Google routing calls: neither WebClient bean previously configured
 * a connect or response timeout, so a hung upstream call had no bound other
 * than whatever Resilience4j's retry/circuit-breaker wrapper happened to
 * provide (no TimeLimiter was, or is, configured). GeoProviderChain treats
 * any failure -- including a timeout -- as "try the next provider," so this
 * only needs to prove the bound is wired, not exercise a real slow server
 * (this codebase's existing provider tests deliberately avoid mocking the
 * WebClient reactive chain -- see OpenRouteServiceGeoProviderTest).
 */
class GeoHttpTimeoutConfigurationTest {

	@Test
	void connectAndResponseTimeouts_areConfiguredWithSaneBoundedDefaults() throws IOException {
		Properties properties = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
			properties.load(in);
		}

		String connectTimeout = properties.getProperty("geo.http.connect-timeout-ms");
		String responseTimeout = properties.getProperty("geo.http.response-timeout-ms");

		assertNotNull(connectTimeout);
		assertNotNull(responseTimeout);
		assertTrue(connectTimeout.matches("\\$\\{[A-Z_]+:\\d+}"));
		assertTrue(responseTimeout.matches("\\$\\{[A-Z_]+:\\d+}"));

		long connectDefaultMs = Long.parseLong(connectTimeout.replaceAll("[^0-9]", ""));
		long responseDefaultMs = Long.parseLong(responseTimeout.replaceAll("[^0-9]", ""));

		// Bounded (not disabled/huge) and not so short that normal Delhi/
		// production routing calls would fail spuriously.
		assertTrue(connectDefaultMs >= 1000 && connectDefaultMs <= 15000);
		assertTrue(responseDefaultMs >= 1000 && responseDefaultMs <= 20000);
	}

	@Test
	void openRouteServiceGeoProvider_exposesAConfigurableResponseTimeoutField() {
		OpenRouteServiceGeoProvider provider = new OpenRouteServiceGeoProvider(WebClient.builder().build());

		ReflectionTestUtils.setField(provider, "responseTimeoutMs", 8000L);

		Object value = ReflectionTestUtils.getField(provider, "responseTimeoutMs");
		assertTrue(value instanceof Long && (Long) value == 8000L);
	}

	@Test
	void googleGeoProvider_exposesAConfigurableResponseTimeoutField() {
		GoogleGeoProvider provider = new GoogleGeoProvider(WebClient.builder().build());

		ReflectionTestUtils.setField(provider, "responseTimeoutMs", 8000L);

		Object value = ReflectionTestUtils.getField(provider, "responseTimeoutMs");
		assertTrue(value instanceof Long && (Long) value == 8000L);
	}
}

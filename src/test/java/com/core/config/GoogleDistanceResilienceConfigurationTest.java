package com.core.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/*
 * Regression coverage for the fix to the googleDistance Resilience4j
 * instance-name mismatch: GoogleGeoProvider#calculateDistanceAndTime is
 * annotated @Retry(name = "googleDistance")/@CircuitBreaker(name =
 * "googleDistance"), but application.properties previously only configured a
 * *different* instance, "googleGeo" (used by resolveState/isAirport), so the
 * distance/duration call -- the one that feeds A-to-B-to-C-to-A billing --
 * silently ran under Resilience4j's untuned library defaults. This asserts
 * the exact instance name the annotation uses is actually configured, with
 * conservative values matching the already-reviewed googleGeo/orsDistance
 * settings rather than new, untested ones.
 */
class GoogleDistanceResilienceConfigurationTest {

	private Properties applicationProperties() throws IOException {
		Properties properties = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
			properties.load(in);
		}
		return properties;
	}

	@Test
	void googleDistanceRetryInstance_isConfigured_matchingTheAnnotationName() throws IOException {
		Properties properties = applicationProperties();

		assertNotNull(properties.getProperty("resilience4j.retry.instances.googleDistance.max-attempts"));
		assertNotNull(properties.getProperty("resilience4j.retry.instances.googleDistance.wait-duration"));
	}

	@Test
	void googleDistanceCircuitBreakerInstance_isConfigured_matchingTheAnnotationName() throws IOException {
		Properties properties = applicationProperties();

		assertNotNull(properties.getProperty("resilience4j.circuitbreaker.instances.googleDistance.sliding-window-size"));
		assertNotNull(properties.getProperty("resilience4j.circuitbreaker.instances.googleDistance.failure-rate-threshold"));
		assertNotNull(
				properties.getProperty("resilience4j.circuitbreaker.instances.googleDistance.wait-duration-in-open-state"));
	}

	@Test
	void googleDistanceConfig_doesNotIntroduceAggressiveOrUnboundedRetries() throws IOException {
		Properties properties = applicationProperties();

		int maxAttempts = Integer.parseInt(
				properties.getProperty("resilience4j.retry.instances.googleDistance.max-attempts"));

		// Conservative: a bounded retry count, not an invented aggressive
		// value -- mirrors the existing orsDistance/googleGeo instances.
		assertTrue(maxAttempts >= 1 && maxAttempts <= 3);
	}
}

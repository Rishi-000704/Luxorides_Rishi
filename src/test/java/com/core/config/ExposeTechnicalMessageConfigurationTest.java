package com.core.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/*
 * Regression coverage for the fix to fleetovo.errors.expose-technical-message:
 * checked-in application.properties used to hardcode this to the literal
 * "true", overriding GlobalExceptionHandler's own safe "false" code default
 * (see GlobalExceptionHandler#exposeTechnicalMessage) and returning sanitized
 * exception type/message details to every API client by default. Production
 * must not silently enable this -- it must now require an explicit env var
 * to turn on, and default to false when that env var is absent.
 */
class ExposeTechnicalMessageConfigurationTest {

	@Test
	void checkedInApplicationProperties_noLongerHardcodesTrue_isEnvDrivenWithAFalseDefault() throws IOException {
		Properties properties = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
			properties.load(in);
		}

		String configured = properties.getProperty("fleetovo.errors.expose-technical-message");

		assertNotEquals("true", configured);
		assertTrue(
				configured != null && configured.matches("\\$\\{[A-Z_]+:false}"),
				"expected an env-driven property with an explicit false default, was: " + configured);
	}
}

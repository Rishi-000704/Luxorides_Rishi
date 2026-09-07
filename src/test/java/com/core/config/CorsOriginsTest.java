package com.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CorsOriginsTest {

	@Test
	void parse_splitsOnComma_andTrimsWhitespace() {
		List<String> origins = CorsOrigins.parse("https://customer.test, https://fleetovo.test ,http://localhost:3000");

		assertEquals(List.of("https://customer.test", "https://fleetovo.test", "http://localhost:3000"), origins);
	}

	@Test
	void parse_singleOrigin_returnsOneEntry() {
		assertEquals(List.of("https://customer.test"), CorsOrigins.parse("https://customer.test"));
	}

	@Test
	void parse_dropsEmptySegments_fromTrailingOrDoubleCommas() {
		List<String> origins = CorsOrigins.parse("https://customer.test,,https://fleetovo.test,");

		assertEquals(List.of("https://customer.test", "https://fleetovo.test"), origins);
	}
}

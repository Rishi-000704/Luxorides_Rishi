package com.core.location.util;

import java.util.Locale;
import java.util.Map;

public final class CityNameNormalizer {

	private CityNameNormalizer() {
	}

	private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
			Map.entry("new delhi", "Delhi"),
			Map.entry("delhi", "Delhi"),
			Map.entry("nct of delhi", "Delhi"),
			Map.entry("national capital territory of delhi", "Delhi"),

			Map.entry("gurgaon", "Gurugram"),
			Map.entry("gurugram", "Gurugram"),

			Map.entry("bombay", "Mumbai"),
			Map.entry("mumbai", "Mumbai"),

			Map.entry("bangalore", "Bengaluru"),
			Map.entry("bengaluru", "Bengaluru")
	);

	public static String normalize(String city) {
		if (city == null || city.isBlank()) {
			return city;
		}

		String cleaned = city.trim().replaceAll("\\s+", " ");
		String key = cleaned.toLowerCase(Locale.ROOT);

		return CITY_ALIASES.getOrDefault(key, cleaned);
	}
}
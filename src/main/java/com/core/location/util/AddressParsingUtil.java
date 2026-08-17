package com.core.location.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.core.models.embedded.AddressSnapshot;

public final class AddressParsingUtil {

	private AddressParsingUtil() {
	}

	private static final Pattern INDIA_PINCODE_PATTERN = Pattern.compile("\\b\\d{6}\\b");

	private static final Set<String> COUNTRIES = Set.of(
			"india",
			"bharat"
	);

	private static final Map<String, String> INDIAN_STATE_ALIASES = Map.ofEntries(
			Map.entry("andhra pradesh", "ANDHRA PRADESH"),
			Map.entry("arunachal pradesh", "ARUNACHAL PRADESH"),
			Map.entry("assam", "ASSAM"),
			Map.entry("bihar", "BIHAR"),
			Map.entry("chhattisgarh", "CHHATTISGARH"),
			Map.entry("goa", "GOA"),
			Map.entry("gujarat", "GUJARAT"),
			Map.entry("haryana", "HARYANA"),
			Map.entry("himachal pradesh", "HIMACHAL PRADESH"),
			Map.entry("jharkhand", "JHARKHAND"),
			Map.entry("karnataka", "KARNATAKA"),
			Map.entry("kerala", "KERALA"),
			Map.entry("madhya pradesh", "MADHYA PRADESH"),
			Map.entry("maharashtra", "MAHARASHTRA"),
			Map.entry("manipur", "MANIPUR"),
			Map.entry("meghalaya", "MEGHALAYA"),
			Map.entry("mizoram", "MIZORAM"),
			Map.entry("nagaland", "NAGALAND"),
			Map.entry("odisha", "ODISHA"),
			Map.entry("orissa", "ODISHA"),
			Map.entry("punjab", "PUNJAB"),
			Map.entry("rajasthan", "RAJASTHAN"),
			Map.entry("sikkim", "SIKKIM"),
			Map.entry("tamil nadu", "TAMIL NADU"),
			Map.entry("telangana", "TELANGANA"),
			Map.entry("tripura", "TRIPURA"),
			Map.entry("uttar pradesh", "UTTAR PRADESH"),
			Map.entry("uttarakhand", "UTTARAKHAND"),
			Map.entry("uttaranchal", "UTTARAKHAND"),
			Map.entry("west bengal", "WEST BENGAL"),
			Map.entry("andaman and nicobar islands", "ANDAMAN AND NICOBAR ISLANDS"),
			Map.entry("chandigarh", "CHANDIGARH"),
			Map.entry(
					"dadra and nagar haveli and daman and diu",
					"DADRA AND NAGAR HAVELI AND DAMAN AND DIU"
			),
			Map.entry(
					"daman and diu",
					"DADRA AND NAGAR HAVELI AND DAMAN AND DIU"
			),
			Map.entry(
					"dadra and nagar haveli",
					"DADRA AND NAGAR HAVELI AND DAMAN AND DIU"
			),
			Map.entry("delhi", "DELHI"),
			Map.entry("new delhi", "DELHI"),
			Map.entry("nct of delhi", "DELHI"),
			Map.entry("national capital territory of delhi", "DELHI"),
			Map.entry("jammu and kashmir", "JAMMU AND KASHMIR"),
			Map.entry("ladakh", "LADAKH"),
			Map.entry("lakshadweep", "LAKSHADWEEP"),
			Map.entry("puducherry", "PUDUCHERRY"),
			Map.entry("pondicherry", "PUDUCHERRY")
	);

	public static String parseCity(AddressSnapshot address) {
		if (address == null) {
			return null;
		}

		return parseCity(address.getFormattedAddress());
	}

	public static String parseCity(String formattedAddress) {
		if (formattedAddress == null || formattedAddress.isBlank()) {
			return null;
		}

		List<String> parts = Arrays.stream(formattedAddress.split(","))
				.map(String::trim)
				.filter(part -> !part.isBlank())
				.toList();

		if (parts.isEmpty()) {
			return null;
		}

		int endIndex = parts.size() - 1;

		while (endIndex >= 0 && isCountry(parts.get(endIndex))) {
			endIndex--;
		}

		if (endIndex < 0) {
			return null;
		}

		List<String> usableParts = parts.subList(0, endIndex + 1);

		for (int i = usableParts.size() - 1; i >= 0; i--) {
			String cleanedPart = cleanAddressPart(usableParts.get(i));

			if (cleanedPart.isBlank()) {
				continue;
			}

			boolean looksLikeState = isIndianStateOrUt(cleanedPart);

			/*
			 * Sector 44, Gurugram, Haryana 122003, India
			 * Skip Haryana and return Gurugram.
			 *
			 * Delhi, India
			 * Return Delhi because it is both the city and state/UT.
			 */
			if (looksLikeState && i > 0) {
				continue;
			}

			return cleanedPart;
		}

		return null;
	}

	public static String parseState(AddressSnapshot address) {
		if (address == null) {
			return null;
		}

		return parseState(address.getFormattedAddress());
	}

	public static String parseState(String formattedAddress) {
		if (formattedAddress == null || formattedAddress.isBlank()) {
			return null;
		}

		/*
		 * First use comma-separated address components. This is the safest
		 * text fallback because Google-formatted addresses normally keep the
		 * state in its own segment.
		 */
		List<String> parts = Arrays.stream(formattedAddress.split(","))
				.map(AddressParsingUtil::cleanAddressPart)
				.filter(part -> !part.isBlank())
				.toList();

		for (int i = parts.size() - 1; i >= 0; i--) {
			String state = INDIAN_STATE_ALIASES.get(normalize(parts.get(i)));

			if (state != null) {
				return state;
			}
		}

		/*
		 * Handles legacy addresses without commas, for example:
		 *
		 * Whitefield Bengaluru Karnataka 560066 India
		 *
		 * State names are matched only at the end of the usable address to
		 * avoid false matches such as "Punjab National Bank" in Delhi.
		 */
		String normalizedAddress = normalize(formattedAddress)
				.replace(',', ' ')
				.replaceAll("\\s+", " ")
				.replaceFirst("\\s+(india|bharat)$", "")
				.trim();

		return INDIAN_STATE_ALIASES.entrySet().stream()
				.sorted(
						Comparator.comparingInt(
								(Map.Entry<String, String> entry) -> entry.getKey().length()
						).reversed()
				)
				.filter(entry ->
						normalizedAddress.equals(entry.getKey())
								|| normalizedAddress.endsWith(" " + entry.getKey())
				)
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse(null);
	}

	public static String normalizeStateName(String state) {
		if (state == null || state.isBlank()) {
			return null;
		}

		String cleanedState = cleanAddressPart(state);
		String canonicalState = INDIAN_STATE_ALIASES.get(normalize(cleanedState));

		return canonicalState != null
				? canonicalState
				: cleanedState.toUpperCase(Locale.ROOT);
	}

	public static boolean looksLikeAirportText(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}

		String normalized = normalize(value);

		if (normalized.isBlank()) {
			return false;
		}

		if (normalized.matches(
				".*\\bairport\\s+(road|flyover|area|colony)\\b.*"
		)) {
			return false;
		}

		if (normalized.matches(
				".*\\b(international|domestic)\\s+airport\\b.*"
		)) {
			return true;
		}

		if (normalized.matches(".*\\bairport\\s+terminal\\b.*")) {
			return true;
		}

		if (normalized.matches(
				".*\\bterminal\\s*[0-9a-z]?\\b.*"
		) && normalized.contains("airport")) {
			return true;
		}

		return normalized.matches(".*\\bairport\\b.*");
	}

	private static boolean isCountry(String value) {
		return COUNTRIES.contains(normalize(value));
	}

	private static boolean isIndianStateOrUt(String value) {
		return INDIAN_STATE_ALIASES.containsKey(normalize(value));
	}

	private static String cleanAddressPart(String value) {
		if (value == null) {
			return "";
		}

		return INDIA_PINCODE_PATTERN.matcher(value)
				.replaceAll("")
				.replace("&", " and ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}

		return cleanAddressPart(value)
				.toLowerCase(Locale.ROOT)
				.trim();
	}
}
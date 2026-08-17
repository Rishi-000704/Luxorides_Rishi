package com.core.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateFormatUtil {

	private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
			.withZone(ZoneId.of("Asia/Kolkata"));

	public static String display(Instant instant) {
		if (instant == null) {
			return "";
		}
		return DISPLAY_FORMAT.format(instant);
	}
}
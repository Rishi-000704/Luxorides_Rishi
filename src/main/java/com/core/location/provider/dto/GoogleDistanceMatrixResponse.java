package com.core.location.provider.dto;

import java.util.List;

public record GoogleDistanceMatrixResponse(
		String status,
		String error_message,
		List<Row> rows
) {

	public record Row(
			List<Element> elements
	) {
	}

	public record Element(
			String status,
			Value distance,
			Value duration
	) {
	}

	public record Value(
			String text,
			Long value
	) {
	}
}
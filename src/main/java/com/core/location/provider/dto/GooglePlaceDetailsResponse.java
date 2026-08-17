package com.core.location.provider.dto;

import java.util.List;

public record GooglePlaceDetailsResponse(
		String status,
		Result result
) {

	public record Result(
			List<String> types,
			String name,
			String formatted_address,
			List<AddressComponent> address_components
	) {
	}

	public record AddressComponent(
			String long_name,
			String short_name,
			List<String> types
	) {
	}
}
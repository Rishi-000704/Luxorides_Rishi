package com.core.dtos.common;

public record DisplayAddressDTO(
	String formattedAddress,
	String city,
	String state,
	String pincode,
	String countryCode
) {}

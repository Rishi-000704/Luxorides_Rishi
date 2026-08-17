package com.core.dtos.config;

import com.core.dtos.common.DisplayAddressDTO;

public record OrganizationUpdateRequest(
		String orgName,
		DisplayAddressDTO address,
		String pan,
		String cin,
		String gstin,
		String phone,
		String alternatePhone,
		String email,
		String websiteLink
		) {

}

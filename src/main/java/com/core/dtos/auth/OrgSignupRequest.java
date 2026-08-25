package com.core.dtos.auth;

import com.core.dtos.common.DisplayAddressDTO;
import com.core.models.embedded.Name;

public record OrgSignupRequest(
		String orgId,
		String orgName,
		DisplayAddressDTO address,
		String pan,
		String cin,
		String gstin,
		String phone,
		String email,

		Name adminName,
		String adminPhone,
		String adminEmail,
		String adminPassword
) {
}

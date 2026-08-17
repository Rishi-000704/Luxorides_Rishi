package com.core.dtos.config;

import com.core.dtos.common.DisplayAddressDTO;
import com.core.models.embedded.Name;

public record EmployeeRequest(String id,
		String userId,
		Name name,
		String email,
		String phone,
		DisplayAddressDTO address
) {

}

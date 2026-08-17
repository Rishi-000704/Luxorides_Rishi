package com.core.dtos.auth;

import java.time.Instant;
import java.util.List;

import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.models.enums.Authority;

public record EmployeeMeResponse(
		String id,
		String orgId,
		String userId,
		Name name,
		String email,
		String phone,
		DisplayAddress address,
		String pic,
		List<Authority> authorities,
		Instant createdAt,
		Instant updatedAt,
		String createdBy,
		String updatedBy
) {
}

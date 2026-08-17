package com.core.dtos.config;

import java.util.List;

import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.models.enums.Authority;

public record EmployeeListItem(
        String id,
        String orgId,
        String userId,

        Name name,
        String email,
        String phone,
        DisplayAddress address,
        String pic,

        Boolean enabled,
        List<Authority> authorities
) {
}
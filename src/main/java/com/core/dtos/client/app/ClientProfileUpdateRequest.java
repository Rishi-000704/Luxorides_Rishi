package com.core.dtos.client.app;

import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;

import lombok.Data;

@Data
public class ClientProfileUpdateRequest {
    private Name name;
    private String email;
    private DisplayAddress address;
}

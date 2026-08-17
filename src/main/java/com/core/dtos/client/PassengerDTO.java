package com.core.dtos.client;

import java.time.Instant;

import com.core.dtos.common.NameDTO;
import com.core.validation.ValidPhone;

public record PassengerDTO(String id, String orgId, String clientId, NameDTO name,@ValidPhone String phone, String email,
		
		Instant createdAt, Instant updatedAt, String createdBy, String updatedBy
		) {
}

package com.core.dtos.client;

import java.time.Instant;
import java.util.List;

import com.core.dtos.common.DisplayAddressDTO;
import com.core.dtos.common.NameDTO;
import com.core.validation.ValidPhone;

import jakarta.validation.constraints.NotBlank;

public record ClientDTO(String id, String orgId, String userId, NameDTO name, String email,@ValidPhone @NotBlank String phone,
		DisplayAddressDTO address, String pic, Boolean supplier, List<ClientBillingEntityDTO> billingEntities,
		List<PassengerDTO> passengers,
		Instant createdAt, Instant updatedAt, String createdBy, String updatedBy
		) {
}

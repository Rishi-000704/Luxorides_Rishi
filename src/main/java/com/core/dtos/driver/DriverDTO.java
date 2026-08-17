package com.core.dtos.driver;

import java.time.Instant;

import com.core.dtos.common.DisplayAddressDTO;
import com.core.dtos.common.NameDTO;
import com.core.models.enums.OwnershipType;
import com.core.validation.ValidPhone;

import jakarta.validation.constraints.NotBlank;

public record DriverDTO(String id, String orgId, String clientId, NameDTO clientName,

		NameDTO name, NameDTO fatherName,

		String gender, @ValidPhone @NotBlank String phone,@ValidPhone String alternatePhone,

		DisplayAddressDTO address,

		String adharNumber, String licenseNumber, String pic,

		OwnershipType ownership,
		
		Instant createdAt, Instant updatedAt, String createdBy, String updatedBy
		) {
}

package com.core.dtos.client;

import java.time.Instant;

import com.core.dtos.common.DisplayAddressDTO;
import com.core.validation.ValidPhone;

public record ClientBillingEntityDTO(String id, String orgId, String brandName, String legalName,
		DisplayAddressDTO address, String cin, String gstin,@ValidPhone String phone, String email, String businessType,
		Instant createdAt, Instant updatedAt, String createdBy, String updatedBy
		) {
}

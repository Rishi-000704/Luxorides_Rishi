package com.core.dtos.config;

import com.core.dtos.common.DisplayAddressDTO;

public record BillingEntityRequest(
		String id,
		String brandName,
		String legalName,
		DisplayAddressDTO address,
		String cin,
		String gstin,
		String phone,
		String alternatePhone,
		String email,
		String businessType,
		String bankName,
		String accountName,
		String accountNumber,
		String ifsc,
		String upiId,
		String termsAndConditions,
		Integer gstRate
) {
}
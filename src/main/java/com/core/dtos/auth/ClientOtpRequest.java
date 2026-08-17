package com.core.dtos.auth;

import com.core.validation.ValidPhone;

import jakarta.validation.constraints.NotBlank;

public record ClientOtpRequest(

		@NotBlank @ValidPhone String mobileNumber,

		String orgId

) {
}

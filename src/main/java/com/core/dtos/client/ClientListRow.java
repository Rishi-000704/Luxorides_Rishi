package com.core.dtos.client;

import com.core.validation.ValidPhone;

public record ClientListRow(String clientId, String displayName, @ValidPhone String phone, String email,
		Boolean supplier) {

}

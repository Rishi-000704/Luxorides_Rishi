package com.core.dtos.driver;

import com.core.dtos.common.DisplayAddressDTO;
import com.core.dtos.common.NameDTO;
import com.core.models.enums.OwnershipType;
import com.core.validation.ValidPhone;

public record DriverListItem(String driverid, NameDTO employedBy,

		NameDTO name, @ValidPhone String phone,

		DisplayAddressDTO address, String licenseNumber, String pic,

		OwnershipType ownership) {

}

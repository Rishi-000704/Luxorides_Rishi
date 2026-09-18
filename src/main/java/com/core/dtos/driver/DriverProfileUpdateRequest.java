package com.core.dtos.driver;

import com.core.dtos.common.AddressSnapshotDTO;
import com.core.dtos.common.DisplayAddressDTO;
import com.core.dtos.common.NameDTO;
import com.core.validation.ValidPhone;

import jakarta.validation.constraints.Email;

/*
 * Deliberately narrower than DriverDTO -- a driver may update their own
 * name/gender/alternate phone/email/address/garage location/experience, but
 * not clientId, ownership, phone (the login identity), or
 * adharNumber/licenseNumber (KYC-controlled via the document verification
 * flow, see DriverDocumentService), all of which remain ops-only through
 * DriverController's existing PUT /employee/drivers/{id}.
 */
public record DriverProfileUpdateRequest(
		NameDTO name,
		String gender,
		@ValidPhone String alternatePhone,
		@Email String email,
		DisplayAddressDTO address,
		AddressSnapshotDTO garageLocation,
		Integer experienceYears
) {
}

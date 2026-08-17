package com.core.util;

import com.core.dtos.common.AddressSnapshotDTO;
import com.core.dtos.common.DisplayAddressDTO;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.DisplayAddress;

public final class AddressUtil {
	private AddressUtil() {
	}

	public static AddressSnapshot toAddressSnapshot(AddressSnapshotDTO dto) {
		if (dto == null)
			return null;

		return new AddressSnapshot(dto.formattedAddress(), dto.googlePlaceId(), dto.latitude(), dto.longitude());
	}

	public static DisplayAddress toDisplayAddress(DisplayAddressDTO dto) {
		if (dto == null)
			return null;
		return new DisplayAddress(dto.formattedAddress(), dto.city(), dto.state(), dto.pincode(), dto.countryCode());
	}
}

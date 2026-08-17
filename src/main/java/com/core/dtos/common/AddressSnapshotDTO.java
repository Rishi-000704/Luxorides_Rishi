package com.core.dtos.common;

import com.core.models.embedded.AddressSnapshot;

public record AddressSnapshotDTO(String formattedAddress, String googlePlaceId, Double latitude, Double longitude) {
	 public AddressSnapshot toAddressSnapshot() {
	        AddressSnapshot snapshot = new AddressSnapshot();
	        snapshot.setFormattedAddress(this.formattedAddress);
	        snapshot.setGooglePlaceId(this.googlePlaceId);
	        snapshot.setLatitude(this.latitude);
	        snapshot.setLongitude(this.longitude);
	        return snapshot;
	    }
}

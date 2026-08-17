package com.core.dtos.config;

import com.core.dtos.common.AddressSnapshotDTO;

public record GarageRequest(String city, AddressSnapshotDTO garageLocation) {

}

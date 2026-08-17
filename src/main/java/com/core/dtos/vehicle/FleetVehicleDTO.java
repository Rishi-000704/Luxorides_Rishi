package com.core.dtos.vehicle;

import java.time.Instant;

import com.core.dtos.client.ClientDTO;
import com.core.dtos.common.AddressSnapshotDTO;
import com.core.models.enums.OwnershipType;

public record FleetVehicleDTO(

    String id,
    String orgId,

    String masterVehicleId,
    String clientId,

    String registrationNumber,
    OwnershipType ownership,

    AddressSnapshotDTO garageLocation,

    MasterVehicleDTO masterVehicle,
    ClientDTO client,

    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy
) {}

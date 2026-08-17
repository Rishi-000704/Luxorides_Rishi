package com.core.dtos.estimate;

import java.time.Instant;
import java.util.List;

import com.core.dtos.common.AddressSnapshotDTO;
import com.core.dtos.common.MoneyDTO;
import com.core.models.enums.DutyType;
import com.core.models.enums.PackageScope;

public record PublicEstimateEntryDTO(

        String id,

        String estimateEntryId,

        PackageDetails packageDetails,

        VehicleDetails requestedVehicle,

        Instant reportingTime,

        Instant dropTime,

        AddressSnapshotDTO reportingLocation,

        AddressSnapshotDTO dropLocation,

        Integer runningDays,

        Integer extraChargeableDistance,

        Float extraChargeableTime,

        boolean nightChargeable,

        List<ExtraChargeDetails> extraCharges,

        MoneyDTO lineTotal) {

    public record PackageDetails(

            String packageId,

            PackageScope scope,

            DutyType dutyType,

            Integer includedTime,

            Integer includedDistance,

            String unit,

            MoneyDTO baseFare,

            MoneyDTO extraPerKm,

            MoneyDTO extraPerHour,

            MoneyDTO nightCharge) {
    }

    public record VehicleDetails(

            String id,

            String name,

            String image,

            String brand,

            String category,

            String seats,

            String doors,

            String transmissionType,

            String fuelSystem,

            String modelYear) {
    }

    public record ExtraChargeDetails(

            String id,

            String description,

            MoneyDTO amount) {
    }
}

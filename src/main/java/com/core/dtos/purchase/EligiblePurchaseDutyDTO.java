package com.core.dtos.purchase;

import java.time.Instant;

import com.core.models.embedded.Money;
import com.core.models.enums.DutyStatus;

public record EligiblePurchaseDutyDTO(
        String bookingId,
        String bookingEntryId,
        String dutyId,
        DutyStatus status,
        String clientId,
        String clientName,
        String vendorId,
        String masterVehicleId,
        String vehicleName,
        String fleetVehicleId,
        String fleetVehicleName,
        String driverId,
        String driverName,
        Instant reportingTime,
        Instant dropTime,
        Integer runningDays,
        Integer extraChargebleDistance,
        Float extraChargebleTime,
        Boolean nightChargeble,
        Money salesDutyTotal
) {
}
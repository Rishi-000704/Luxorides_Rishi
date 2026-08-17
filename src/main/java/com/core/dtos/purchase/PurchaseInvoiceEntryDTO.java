package com.core.dtos.purchase;

import java.time.Instant;

import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;

public record PurchaseInvoiceEntryDTO(
        String id,
        String bookingId,
        String bookingEntryId,
        String dutyId,

        PackageSnapshot packageSnapshot,

        String masterVehicleId,

        Instant reportingTime,
        Instant dropTime,

        Integer runningDays,
        Integer extraChargebleDistance,
        Float extraChargebleTime,
        Boolean nightChargeble,

        Money chargebleBaseFare,
        Money extraChargeDistance,
        Money extraChargeTime,
        Money nightCharge,
        Money extraChargesTotal,
        Money dutyTotal,

        String remarks
) {
}
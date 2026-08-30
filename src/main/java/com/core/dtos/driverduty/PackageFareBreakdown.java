package com.core.dtos.driverduty;

import java.math.BigDecimal;

/**
 * Display-ready breakdown of the package/rate-card figures {@link com.core.util.BookingUtil}
 * already computed onto the completed {@link com.core.models.BookingEntry} (extra chargeable
 * distance/time against the org's configured package, at its configured rates). Purely a
 * read-out of numbers that already fed {@code BookingEntry.dutyTotal} -- never a second,
 * independent fare calculation.
 */
public record PackageFareBreakdown(
        String dutyType,
        String packageUnit,
        Integer includedDistanceKm,
        Integer includedTimeUnits,
        BigDecimal baseFareAmount,
        Integer extraDistanceKm,
        BigDecimal extraDistanceRatePerKm,
        BigDecimal extraDistanceCharge,
        Double extraTimeHours,
        BigDecimal extraTimeRatePerHour,
        BigDecimal extraTimeCharge,
        Long projectedTotalDurationSeconds
) {}

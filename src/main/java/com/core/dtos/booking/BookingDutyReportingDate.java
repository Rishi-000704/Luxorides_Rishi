package com.core.dtos.booking;

import java.time.Instant;

import com.core.models.enums.DutyStatus;

public record BookingDutyReportingDate(
        String dutyId,
        Instant reportingTime,
        Instant dropTime,
        DutyStatus status
) {
}
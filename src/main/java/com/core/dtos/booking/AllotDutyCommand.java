package com.core.dtos.booking;

public record AllotDutyCommand(
        String bookingId,
        String dutyId,
        String supplierId,
        String driverId,
        String fleetVehicleId
) {}
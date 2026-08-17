package com.core.dtos.purchase;

import com.core.dtos.common.MoneyDTO;
import com.core.models.enums.DutyType;
import com.core.models.enums.PackageScope;

public record PurchasePackageOptionDTO(
        String id,
        PackageScope scope,
        String clientId,
        String masterVehicleId,
        DutyType dutyType,
        Integer time,
        String unit,
        Integer distance,
        MoneyDTO baseFare,
        MoneyDTO extraPerKM,
        MoneyDTO extraPerHS,
        MoneyDTO nightCharge,
        Boolean forSales,
        String location
) {
}
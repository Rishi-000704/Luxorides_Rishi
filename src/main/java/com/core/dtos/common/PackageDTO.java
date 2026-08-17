package com.core.dtos.common;

import java.time.Instant;

import com.core.dtos.client.ClientDTO;
import com.core.dtos.vehicle.MasterVehicleDTO;
import com.core.models.enums.DutyType;
import com.core.models.enums.PackageScope;

public record PackageDTO(

		// Identity
		String id, String orgId,

		// Scope & ownership
		PackageScope scope, String clientId,

		// Vehicle reference
		String masterVehicleId,

		// Package definition
		DutyType dutyType, Integer time, String unit, Integer distance,

		// Pricing
		MoneyDTO baseFare, MoneyDTO extraPerKM, MoneyDTO extraPerHS, MoneyDTO nightCharge,

		// Visibility & applicability
		Boolean forSales, String location,

		MasterVehicleDTO masterVehicle, ClientDTO client,

		Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
}

package com.core.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.maintenance.MaintenancePredictionResponse;
import com.core.dtos.maintenance.VehicleMaintenanceRecordRequest;
import com.core.dtos.maintenance.VehicleMaintenanceRecordResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.FleetVehicle;
import com.core.models.VehicleMaintenanceRecord;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.FleetVehicleRepository;
import com.core.repositories.VehicleMaintenanceRecordRepository;

import lombok.RequiredArgsConstructor;

/*
 * Predictive maintenance from real data only: odometer readings already
 * captured at real duty start/end checkpoints, and real admin-entered
 * service records. No fabricated telemetry/IoT/sensor data -- purely
 * mileage/time-since-last-service, honestly scoped to what this system
 * actually has.
 */
@Service
@RequiredArgsConstructor
public class VehicleMaintenanceService {

	private static final int SERVICE_INTERVAL_KM = 10_000;
	private static final long SERVICE_INTERVAL_DAYS = 182; // ~6 months
	private static final double OVERDUE_MULTIPLIER = 1.2;

	private final VehicleMaintenanceRecordRepository recordRepository;
	private final FleetVehicleRepository fleetVehicleRepository;
	private final BookingEntryRepository bookingEntryRepository;

	@Transactional
	public VehicleMaintenanceRecordResponse recordService(String orgId, VehicleMaintenanceRecordRequest request) {
		if (request.fleetVehicleId() == null || request.serviceType() == null || request.serviceType().isBlank()
				|| request.serviceDate() == null || request.odometerKmAtService() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vehicle, service type, date, and odometer are required");
		}

		fleetVehicleRepository.findByIdAndOrgId(request.fleetVehicleId(), orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Vehicle not found"));

		VehicleMaintenanceRecord record = new VehicleMaintenanceRecord();
		record.setOrgId(orgId);
		record.setFleetVehicleId(request.fleetVehicleId());
		record.setServiceType(request.serviceType());
		record.setServiceDate(request.serviceDate());
		record.setOdometerKmAtService(request.odometerKmAtService());
		record.setCost(request.cost());
		record.setRemarks(request.remarks());

		return toResponse(recordRepository.save(record));
	}

	@Transactional(readOnly = true)
	public List<VehicleMaintenanceRecordResponse> history(String orgId, String fleetVehicleId) {
		return recordRepository.findByFleetVehicleIdAndOrgIdOrderByServiceDateDesc(fleetVehicleId, orgId)
				.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<MaintenancePredictionResponse> predict(String orgId) {
		return fleetVehicleRepository.findByOrgId(orgId).stream()
				.map(vehicle -> predictOne(orgId, vehicle))
				.toList();
	}

	private MaintenancePredictionResponse predictOne(String orgId, FleetVehicle vehicle) {
		Integer currentOdometer = bookingEntryRepository.findMaxClosingKmForVehicle(orgId, vehicle.getId());

		VehicleMaintenanceRecord lastService = recordRepository
				.findFirstByFleetVehicleIdAndOrgIdOrderByServiceDateDesc(vehicle.getId(), orgId)
				.orElse(null);

		Integer kmSinceService = null;
		Long daysSinceService = null;

		if (currentOdometer != null) {
			int baselineKm = lastService != null ? lastService.getOdometerKmAtService() : 0;
			kmSinceService = Math.max(0, currentOdometer - baselineKm);
		}

		if (lastService != null) {
			daysSinceService = Duration.between(lastService.getServiceDate(), Instant.now()).toDays();
		}

		boolean dueForService = (kmSinceService != null && kmSinceService >= SERVICE_INTERVAL_KM)
				|| (daysSinceService != null && daysSinceService >= SERVICE_INTERVAL_DAYS);

		boolean overdue = (kmSinceService != null && kmSinceService >= SERVICE_INTERVAL_KM * OVERDUE_MULTIPLIER)
				|| (daysSinceService != null && daysSinceService >= SERVICE_INTERVAL_DAYS * OVERDUE_MULTIPLIER);

		String vehicleName = vehicle.getMasterVehicle() != null ? vehicle.getMasterVehicle().getName() : null;

		return new MaintenancePredictionResponse(vehicle.getId(), vehicleName, vehicle.getRegistrationNumber(),
				currentOdometer, kmSinceService, daysSinceService, dueForService, overdue);
	}

	private VehicleMaintenanceRecordResponse toResponse(VehicleMaintenanceRecord r) {
		return new VehicleMaintenanceRecordResponse(r.getId(), r.getFleetVehicleId(), r.getServiceType(),
				r.getServiceDate(), r.getOdometerKmAtService(), r.getCost(), r.getRemarks());
	}
}

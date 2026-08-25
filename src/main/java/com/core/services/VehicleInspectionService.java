package com.core.services;

import java.io.IOException;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.VehicleInspectionRequest;
import com.core.dtos.driverduty.VehicleInspectionResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.VehicleInspection;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.VehicleInspectionRepository;
import com.core.services.common.FileService;

import lombok.RequiredArgsConstructor;

/*
 * Lives under DriverAppController (Bearer JWT), not the duty-execution-token
 * controller -- readiness/inspection submission happens before startDuty
 * mints the execution token in the current screen order, so token-scoping
 * it would mean reordering that existing (working) flow. Upserts by dutyId
 * (latest-only, matching DriverDutyLiveLocation's pattern) since readiness
 * is meant to be submitted once per duty, with retakes overwriting rather
 * than accumulating rows.
 */
@Service
@RequiredArgsConstructor
public class VehicleInspectionService {

	private static final long MAX_PHOTO_SIZE = 10L * 1024L * 1024L;

	private final DriverRepository driverRepository;
	private final BookingEntryRepository bookingEntryRepository;
	private final VehicleInspectionRepository vehicleInspectionRepository;
	private final FileService fileService;

	@Transactional
	public VehicleInspectionResponse submitInspection(
			String orgId,
			String userId,
			String dutyId,
			VehicleInspectionRequest payload,
			MultipartFile exteriorFront,
			MultipartFile exteriorBack,
			MultipartFile exteriorLeft,
			MultipartFile exteriorRight,
			MultipartFile interiorDashboard,
			MultipartFile interiorFrontSeats,
			MultipartFile interiorBackSeats,
			MultipartFile interiorBootSpace
	) throws IOException {
		Driver driver = driverRepository.findByUserId(userId)
				.filter(d -> orgId.equals(d.getOrgId()))
				.orElseThrow(() -> new NotFoundException(ErrorCode.DRIVER_NOT_FOUND, "Driver record not found for this account"));

		BookingEntry entry = bookingEntryRepository.findForDriverSelf(orgId, driver.getId(), dutyId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		if (payload.exteriorCondition() == null || payload.interiorCondition() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Exterior and interior condition ratings are required");
		}

		VehicleInspection inspection = vehicleInspectionRepository.findByDutyIdAndOrgId(dutyId, orgId)
				.orElseGet(VehicleInspection::new);

		inspection.setOrgId(orgId);
		inspection.setDutyId(dutyId);
		inspection.setDriverId(driver.getId());
		inspection.setFleetVehicleId(entry.getFleetVehicleId());
		inspection.setExteriorCondition(payload.exteriorCondition());
		inspection.setInteriorCondition(payload.interiorCondition());
		inspection.setDamageNotes(payload.damageNotes());
		inspection.setSubmittedAt(Instant.now());

		inspection.setExteriorFrontPhoto(saveImage(exteriorFront, inspection.getExteriorFrontPhoto()));
		inspection.setExteriorBackPhoto(saveImage(exteriorBack, inspection.getExteriorBackPhoto()));
		inspection.setExteriorLeftPhoto(saveImage(exteriorLeft, inspection.getExteriorLeftPhoto()));
		inspection.setExteriorRightPhoto(saveImage(exteriorRight, inspection.getExteriorRightPhoto()));
		inspection.setInteriorDashboardPhoto(saveImage(interiorDashboard, inspection.getInteriorDashboardPhoto()));
		inspection.setInteriorFrontSeatsPhoto(saveImage(interiorFrontSeats, inspection.getInteriorFrontSeatsPhoto()));
		inspection.setInteriorBackSeatsPhoto(saveImage(interiorBackSeats, inspection.getInteriorBackSeatsPhoto()));
		inspection.setInteriorBootSpacePhoto(saveImage(interiorBootSpace, inspection.getInteriorBootSpacePhoto()));

		VehicleInspection saved = vehicleInspectionRepository.save(inspection);

		return new VehicleInspectionResponse(saved.getId(), true);
	}

	/** Keeps the existing stored filename when a retry omits a photo that was already uploaded. */
	private String saveImage(MultipartFile file, String existing) throws IOException {
		if (file == null || file.isEmpty()) {
			return existing;
		}

		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Only image files are allowed");
		}

		if (file.getSize() > MAX_PHOTO_SIZE) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Image size cannot exceed 10 MB");
		}

		return fileService.saveFile(file);
	}
}

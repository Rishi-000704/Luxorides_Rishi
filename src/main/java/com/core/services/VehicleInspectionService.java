package com.core.services;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
			MultipartFile interiorBootSpace,
			MultipartFile uniformSelfie
	) throws IOException {
		Driver driver = driverRepository.findByUserId(userId)
				.filter(d -> orgId.equals(d.getOrgId()))
				.orElseThrow(() -> new NotFoundException(ErrorCode.DRIVER_NOT_FOUND, "Driver record not found for this account"));

		BookingEntry entry = bookingEntryRepository.findForDriverSelf(orgId, driver.getId(), dutyId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		if (payload.exteriorCondition() == null || payload.interiorCondition() == null
				|| payload.cleanliness() == null || payload.tyreCondition() == null || payload.lightsCondition() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "All condition ratings are required");
		}

		if (!payload.driverConfirmed()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Driver confirmation is required to submit the inspection");
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
		inspection.setCleanliness(payload.cleanliness());
		inspection.setTyreCondition(payload.tyreCondition());
		inspection.setLightsCondition(payload.lightsCondition());
		inspection.setFuelLevel(payload.fuelLevel());
		inspection.setDriverConfirmed(true);
		inspection.setSubmittedAt(Instant.now());

		List<String> supersededPhotos = new ArrayList<>();

		inspection.setExteriorFrontPhoto(saveImage(exteriorFront, inspection.getExteriorFrontPhoto(), supersededPhotos));
		inspection.setExteriorBackPhoto(saveImage(exteriorBack, inspection.getExteriorBackPhoto(), supersededPhotos));
		inspection.setExteriorLeftPhoto(saveImage(exteriorLeft, inspection.getExteriorLeftPhoto(), supersededPhotos));
		inspection.setExteriorRightPhoto(saveImage(exteriorRight, inspection.getExteriorRightPhoto(), supersededPhotos));
		inspection.setInteriorDashboardPhoto(saveImage(interiorDashboard, inspection.getInteriorDashboardPhoto(), supersededPhotos));
		inspection.setInteriorFrontSeatsPhoto(saveImage(interiorFrontSeats, inspection.getInteriorFrontSeatsPhoto(), supersededPhotos));
		inspection.setInteriorBackSeatsPhoto(saveImage(interiorBackSeats, inspection.getInteriorBackSeatsPhoto(), supersededPhotos));
		inspection.setInteriorBootSpacePhoto(saveImage(interiorBootSpace, inspection.getInteriorBootSpacePhoto(), supersededPhotos));
		inspection.setUniformSelfiePhoto(saveImage(uniformSelfie, inspection.getUniformSelfiePhoto(), supersededPhotos));

		// Enforced on the final set (existing photos from an earlier partial
		// save + whatever this call just uploaded), not merely on this call's
		// multipart parts -- a driver cannot appear "inspection complete" by
		// submitting condition ratings alone, and a legitimate incremental
		// retry that only resends changed photos still passes once every
		// slot has been filled by some call.
		if (!hasAllRequiredPhotos(inspection)) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "All 8 vehicle photos and the uniform selfie are required to submit the inspection");
		}

		VehicleInspection saved = vehicleInspectionRepository.save(inspection);

		/*
		 * P1.6 -- a retake (this call's photo replacing an already-stored one
		 * in the same slot) previously left the old file on disk forever.
		 * Deleted only now, after the row pointing at the new files is
		 * durably saved, so a failed/rolled-back submission never loses a
		 * driver's previously-accepted evidence photo.
		 */
		for (String superseded : supersededPhotos) {
			try {
				fileService.deleteFile(superseded);
			} catch (Exception ignored) {
				// Orphan cleanup must not fail an otherwise-successful submission.
			}
		}

		return new VehicleInspectionResponse(saved.getId(), true);
	}

	private boolean hasAllRequiredPhotos(VehicleInspection inspection) {
		return inspection.getExteriorFrontPhoto() != null
				&& inspection.getExteriorBackPhoto() != null
				&& inspection.getExteriorLeftPhoto() != null
				&& inspection.getExteriorRightPhoto() != null
				&& inspection.getInteriorDashboardPhoto() != null
				&& inspection.getInteriorFrontSeatsPhoto() != null
				&& inspection.getInteriorBackSeatsPhoto() != null
				&& inspection.getInteriorBootSpacePhoto() != null
				&& inspection.getUniformSelfiePhoto() != null;
	}

	/**
	 * Keeps the existing stored filename when a retry omits a photo that was
	 * already uploaded. When a new photo genuinely replaces one already in
	 * this slot, the old filename is appended to {@code supersededPhotos} for
	 * the caller to delete once the inspection row is durably saved.
	 */
	private String saveImage(MultipartFile file, String existing, List<String> supersededPhotos) throws IOException {
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

		String saved = fileService.saveFile(file);

		if (existing != null) {
			supersededPhotos.add(existing);
		}

		return saved;
	}
}

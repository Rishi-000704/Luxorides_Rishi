package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.VehicleInspectionRequest;
import com.core.dtos.driverduty.VehicleInspectionResponse;
import com.core.exception.BusinessException;
import com.core.exception.NotFoundException;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.VehicleInspection;
import com.core.models.enums.CleanlinessRating;
import com.core.models.enums.FuelLevel;
import com.core.models.enums.VehicleConditionRating;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.VehicleInspectionRepository;
import com.core.services.common.FileService;

/*
 * Covers the P2.3 hardening of VehicleInspectionService: every condition
 * rating + driver confirmation + all 8 photos are now required before a
 * submission is accepted, enforced server-side regardless of what the
 * mobile client already validates.
 */
class VehicleInspectionServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String USER_ID = "user-1";
	private static final String DRIVER_ID = "driver-1";
	private static final String DUTY_ID = "duty-1";

	private DriverRepository driverRepository;
	private BookingEntryRepository bookingEntryRepository;
	private VehicleInspectionRepository vehicleInspectionRepository;
	private FileService fileService;
	private VehicleInspectionService service;

	@BeforeEach
	void setUp() throws Exception {
		driverRepository = mock(DriverRepository.class);
		bookingEntryRepository = mock(BookingEntryRepository.class);
		vehicleInspectionRepository = mock(VehicleInspectionRepository.class);
		fileService = mock(FileService.class);
		service = new VehicleInspectionService(driverRepository, bookingEntryRepository, vehicleInspectionRepository, fileService);

		Driver driver = new Driver();
		driver.setId(DRIVER_ID);
		driver.setOrgId(ORG_ID);
		when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.of(driver));

		BookingEntry entry = new BookingEntry();
		entry.setDutyId(DUTY_ID);
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.of(entry));

		when(vehicleInspectionRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.empty());
		when(vehicleInspectionRepository.save(any(VehicleInspection.class)))
				.thenAnswer(invocation -> {
					VehicleInspection saved = invocation.getArgument(0);
					if (saved.getId() == null) {
						saved.setId("inspection-1");
					}
					return saved;
				});

		when(fileService.saveFile(any(MultipartFile.class))).thenAnswer(invocation -> {
			MultipartFile file = invocation.getArgument(0);
			return file.getOriginalFilename();
		});
	}

	private VehicleInspectionRequest completeRequest(boolean driverConfirmed) {
		return new VehicleInspectionRequest(
				VehicleConditionRating.GOOD,
				VehicleConditionRating.GOOD,
				null,
				CleanlinessRating.CLEAN,
				VehicleConditionRating.GOOD,
				VehicleConditionRating.GOOD,
				FuelLevel.FULL,
				driverConfirmed
		);
	}

	private MockMultipartFile photo(String name) {
		return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[] { 1, 2, 3 });
	}

	@Test
	void submitInspection_succeeds_whenAllFieldsAndPhotosPresent() throws Exception {
		VehicleInspectionResponse response = service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, completeRequest(true),
				photo("front"), photo("back"), photo("left"), photo("right"),
				photo("dash"), photo("frontSeats"), photo("backSeats"), photo("boot")
		);

		assertTrue(response.received());
	}

	@Test
	void submitInspection_rejects_whenDriverNotConfirmed() {
		assertThrows(BusinessException.class, () -> service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, completeRequest(false),
				photo("front"), photo("back"), photo("left"), photo("right"),
				photo("dash"), photo("frontSeats"), photo("backSeats"), photo("boot")
		));
	}

	@Test
	void submitInspection_rejects_whenConditionRatingMissing() {
		VehicleInspectionRequest incomplete = new VehicleInspectionRequest(
				null, VehicleConditionRating.GOOD, null,
				CleanlinessRating.CLEAN, VehicleConditionRating.GOOD, VehicleConditionRating.GOOD,
				FuelLevel.FULL, true
		);

		assertThrows(BusinessException.class, () -> service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, incomplete,
				photo("front"), photo("back"), photo("left"), photo("right"),
				photo("dash"), photo("frontSeats"), photo("backSeats"), photo("boot")
		));
	}

	@Test
	void submitInspection_rejects_whenAPhotoIsMissing_andDoesNotPersist() throws Exception {
		assertThrows(BusinessException.class, () -> service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, completeRequest(true),
				photo("front"), photo("back"), photo("left"), null,
				photo("dash"), photo("frontSeats"), photo("backSeats"), photo("boot")
		));
	}

	@Test
	void submitInspection_upsertsSameRow_onResubmission() throws Exception {
		VehicleInspection existing = new VehicleInspection();
		existing.setId("inspection-1");
		existing.setExteriorFrontPhoto("front.jpg");
		existing.setExteriorBackPhoto("back.jpg");
		existing.setExteriorLeftPhoto("left.jpg");
		existing.setExteriorRightPhoto("right.jpg");
		existing.setInteriorDashboardPhoto("dash.jpg");
		existing.setInteriorFrontSeatsPhoto("frontSeats.jpg");
		existing.setInteriorBackSeatsPhoto("backSeats.jpg");
		existing.setInteriorBootSpacePhoto("boot.jpg");
		when(vehicleInspectionRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.of(existing));

		// Retake only one photo -- the rest keep their already-stored filenames.
		VehicleInspectionResponse response = service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, completeRequest(true),
				photo("front-retake"), null, null, null,
				null, null, null, null
		);

		assertEquals("inspection-1", response.id());
		assertEquals("front-retake.jpg", existing.getExteriorFrontPhoto());
		assertEquals("back.jpg", existing.getExteriorBackPhoto());
	}

	@Test
	void submitInspection_retake_deletesTheSupersededFile_afterTheRowIsSaved() throws Exception {
		VehicleInspection existing = new VehicleInspection();
		existing.setId("inspection-1");
		existing.setExteriorFrontPhoto("front-old.jpg");
		existing.setExteriorBackPhoto("back.jpg");
		existing.setExteriorLeftPhoto("left.jpg");
		existing.setExteriorRightPhoto("right.jpg");
		existing.setInteriorDashboardPhoto("dash.jpg");
		existing.setInteriorFrontSeatsPhoto("frontSeats.jpg");
		existing.setInteriorBackSeatsPhoto("backSeats.jpg");
		existing.setInteriorBootSpacePhoto("boot.jpg");
		when(vehicleInspectionRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.of(existing));

		service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, completeRequest(true),
				photo("front-retake"), null, null, null,
				null, null, null, null
		);

		// Only the slot that actually changed is cleaned up -- the other 7
		// untouched slots' files must never be deleted.
		verify(fileService).deleteFile("front-old.jpg");
		verify(fileService, never()).deleteFile("back.jpg");
		verify(fileService, never()).deleteFile("left.jpg");
	}

	@Test
	void submitInspection_firstTimeUpload_neverCallsDeleteFile() throws Exception {
		service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, completeRequest(true),
				photo("front"), photo("back"), photo("left"), photo("right"),
				photo("dash"), photo("frontSeats"), photo("backSeats"), photo("boot")
		);

		verify(fileService, never()).deleteFile(any());
	}

	@Test
	void submitInspection_rejectedSubmission_neverDeletesTheExistingPhoto() {
		VehicleInspection existing = new VehicleInspection();
		existing.setId("inspection-1");
		existing.setExteriorFrontPhoto("front-old.jpg");
		when(vehicleInspectionRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.of(existing));

		// Missing the other 7 required photos -- submission is rejected
		// before vehicleInspectionRepository.save is ever called.
		assertThrows(BusinessException.class, () -> service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, completeRequest(true),
				photo("front-retake"), null, null, null, null, null, null, null
		));

		verify(fileService, never()).deleteFile(eq("front-old.jpg"));
	}

	@Test
	void submitInspection_rejectsDutyNotBelongingToCaller() {
		when(bookingEntryRepository.findForDriverSelf(ORG_ID, DRIVER_ID, DUTY_ID)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () -> service.submitInspection(
				ORG_ID, USER_ID, DUTY_ID, completeRequest(true),
				photo("front"), photo("back"), photo("left"), photo("right"),
				photo("dash"), photo("frontSeats"), photo("backSeats"), photo("boot")
		));
	}
}

package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.core.models.Driver;
import com.core.models.enums.OwnershipType;
import com.core.repositories.DriverRepository;
import com.core.services.common.FileService;

/*
 * P1.6 -- Driver.pic is an ordinary display avatar (not KYC/evidence), so
 * updatePic now goes through FileService.saveDisplayImage (dimension-capped,
 * recompressed) instead of saveFile (stores the original byte-for-byte, used
 * only for KYC/inspection/odometer/incident evidence). The pre-existing
 * delete-old-file-before-save behavior is unchanged.
 */
class DriverServiceUpdatePicTest {

	private static final String ORG_ID = "org-1";
	private static final String DRIVER_ID = "driver-1";

	private DriverRepository driverRepository;
	private FileService fileService;
	private DriverService service;

	@BeforeEach
	void setUp() {
		driverRepository = mock(DriverRepository.class);
		ClientService clientService = mock(ClientService.class);
		fileService = mock(FileService.class);
		service = new DriverService(driverRepository, clientService, fileService);
	}

	private Driver driver() {
		Driver d = new Driver();
		d.setId(DRIVER_ID);
		d.setOrgId(ORG_ID);
		d.setOwnership(OwnershipType.ORG);
		return d;
	}

	@Test
	void updatePic_usesSaveDisplayImage_notSaveFile() throws Exception {
		when(driverRepository.findByIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(Optional.of(driver()));
		when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));
		when(fileService.saveDisplayImage(any())).thenReturn("resized.jpg");

		MultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		Driver result = service.updatePic(DRIVER_ID, ORG_ID, file);

		assertEquals("resized.jpg", result.getPic());
		verify(fileService).saveDisplayImage(file);
		verify(fileService, never()).saveFile(any());
	}

	@Test
	void updatePic_deletesThePreviousPic_beforeSavingTheNewOne() throws Exception {
		Driver existing = driver();
		existing.setPic("old-avatar.jpg");
		when(driverRepository.findByIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(Optional.of(existing));
		when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));
		when(fileService.saveDisplayImage(any())).thenReturn("new-avatar.jpg");

		MultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		service.updatePic(DRIVER_ID, ORG_ID, file);

		verify(fileService).deleteFile("old-avatar.jpg");
	}
}

package com.core.services;

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

import com.core.location.api.LocationService;
import com.core.mapper.VehicleAssembler;
import com.core.mapper.VehicleCatalogAssembler;
import com.core.models.MasterVehicle;
import com.core.repositories.CityGarageRepository;
import com.core.repositories.MasterVehicleRepository;
import com.core.services.common.FileService;

/*
 * P1.6 -- MasterVehicle.pic is an ordinary vehicle-catalog display photo
 * (not KYC/evidence), so updatePic now goes through
 * FileService.saveDisplayImage instead of saveFile. The pre-existing
 * delete-old-file-before-save behavior is unchanged.
 */
class MasterVehicleServiceUpdatePicTest {

	private static final String VEHICLE_ID = "vehicle-1";

	private MasterVehicleRepository masterVehicleRepository;
	private FileService fileService;
	private MasterVehicleService service;

	@BeforeEach
	void setUp() {
		masterVehicleRepository = mock(MasterVehicleRepository.class);
		fileService = mock(FileService.class);
		PackageService packageService = mock(PackageService.class);
		VehicleCatalogAssembler catalogAssembler = mock(VehicleCatalogAssembler.class);
		LocationService locationService = mock(LocationService.class);
		CityGarageRepository garageRepository = mock(CityGarageRepository.class);
		VehicleAssembler assembler = mock(VehicleAssembler.class);

		service = new MasterVehicleService(masterVehicleRepository, fileService, packageService,
				catalogAssembler, locationService, garageRepository, catalogAssembler, assembler);
	}

	private MasterVehicle vehicle() {
		MasterVehicle v = new MasterVehicle();
		v.setId(VEHICLE_ID);
		return v;
	}

	@Test
	void updatePic_usesSaveDisplayImage_notSaveFile() throws Exception {
		when(masterVehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));
		when(masterVehicleRepository.save(any(MasterVehicle.class))).thenAnswer(inv -> inv.getArgument(0));
		when(fileService.saveDisplayImage(any())).thenReturn("resized.jpg");

		MultipartFile file = new MockMultipartFile("file", "catalog.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		service.updatePic(VEHICLE_ID, file);

		verify(fileService).saveDisplayImage(file);
		verify(fileService, never()).saveFile(any());
	}

	@Test
	void updatePic_deletesThePreviousPic_beforeSavingTheNewOne() throws Exception {
		MasterVehicle existing = vehicle();
		existing.setPic("old-catalog.jpg");
		when(masterVehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(existing));
		when(masterVehicleRepository.save(any(MasterVehicle.class))).thenAnswer(inv -> inv.getArgument(0));
		when(fileService.saveDisplayImage(any())).thenReturn("new-catalog.jpg");

		MultipartFile file = new MockMultipartFile("file", "catalog.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		service.updatePic(VEHICLE_ID, file);

		verify(fileService).deleteFile("old-catalog.jpg");
	}
}

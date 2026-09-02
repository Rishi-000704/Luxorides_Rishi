package com.core.services;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Driver;
import com.core.models.enums.OwnershipType;
import com.core.repositories.DriverRepository;
import com.core.services.common.FileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DriverService {
	private final DriverRepository driverRepository;
	private final ClientService clientService;
	private final FileService fileService;

	@Transactional
	public Driver saveDriver(Driver driver, String orgId) throws Exception {
		driver.setOrgId(orgId);
		Driver local = this.driverRepository.findByPhoneAndOrgId(driver.getPhone(), driver.getOrgId());
		if (local == null) {
			return this.attachData(this.driverRepository.save(driver));
		} else {
			throw new BusinessException(ErrorCode.DRIVER_ALREADY_EXISTS, "Driver with this phone already registered.");
		}
	}

	@Transactional
	public void deleteDriver(String driverId, String orgId) {
		Driver driver = driverRepository
				.findByIdAndOrgId(driverId, orgId)
				.orElseThrow(() -> new NotFoundException(
						ErrorCode.DRIVER_NOT_FOUND,
						"Cannot delete because driver does not exist."
				));

		if (driver.getPic() != null) {
			fileService.deleteFile(driver.getPic());
		}

		driverRepository.delete(driver);
	}

	@Transactional
	public Driver updateDriver(Driver driver, String orgId) {
		Driver local = this.getDriver(driver.getId(), orgId);
		local.setClientId(driver.getClientId());
		local.setOwnership(driver.getOwnership());
		local.setName(driver.getName());
		local.setFatherName(driver.getFatherName());
		local.setGender(driver.getGender());
		local.setPhone(driver.getPhone());
		local.setAlternatePhone(driver.getAlternatePhone());
		local.setAddress(driver.getAddress());
		local.setAdharNumber(driver.getAdharNumber());
		local.setLicenseNumber(driver.getLicenseNumber());
		return this.attachData(this.driverRepository.save(local));
	}

	@Transactional(readOnly = true)
	public Driver getDriver(String driverId, String orgId) {
		Driver driver = this.driverRepository.findByIdAndOrgId(driverId, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DRIVER_NOT_FOUND, "Driver not found with this ID."));
		return this.attachData(driver);
	}

	@Transactional(readOnly = true)
	public List<Driver> getDriversByClient(String corporateId, String orgId) {
		List<Driver> dList = this.driverRepository.findByClientIdAndOrgId(corporateId, orgId);
		List<Driver> drivers = new ArrayList<>();
		for (Driver driver : dList) {
			drivers.add(this.attachData(driver));
		}
		return drivers;
	}

	@Transactional(readOnly = true)
	public List<Driver> getSelfDrivers(String orgId) {
		List<Driver> dList = this.driverRepository.findByClientIdAndOrgId(null, orgId);
		List<Driver> drivers = new ArrayList<>();
		for (Driver driver : dList) {
			drivers.add(this.attachData(driver));
		}
		return drivers;
	}

	Driver attachData(Driver driver) {
		if (driver.getOwnership().equals(OwnershipType.CLIENT)) {
			driver.setClient(this.clientService.get(driver.getClientId(), driver.getOrgId()));
		}
		return driver;
	}

	@Transactional(readOnly = true)
	public Page<Driver> getDriverPage(String orgId, String searchStr, Pageable pageable) {
		return this.driverRepository.getPage(orgId, searchStr, pageable);
	}

	@Transactional
	public Driver updatePic(String driverId, String orgId, MultipartFile file) {
		try {
			Driver driver = this.getDriver(driverId, orgId);
			if (driver.getPic() != null) {
				this.fileService.deleteFile(driver.getPic());
			}
			driver.setPic(this.fileService.saveDisplayImage(file));
			return this.attachData(this.driverRepository.save(driver));
		} catch (Exception e) {
			e.printStackTrace();
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
		}
	}

}

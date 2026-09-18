package com.core.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.core.dtos.common.DisplayAddressDTO;
import com.core.dtos.common.NameDTO;
import com.core.dtos.driver.DriverDTO;
import com.core.dtos.driver.DriverListItem;
import com.core.models.Driver;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.models.enums.FileAccessCategory;
import com.core.services.common.AuditActorService;
import com.core.services.common.FileAccessTokenService;
import com.core.util.AddressUtil;

@Component
public class DriverAssembler {

	private final AuditActorService auditActorService;
	private final FileAccessTokenService fileAccessTokenService;

	public DriverAssembler(AuditActorService auditActorService, FileAccessTokenService fileAccessTokenService) {
		this.auditActorService = auditActorService;
		this.fileAccessTokenService = fileAccessTokenService;
	}

	/* ========================= FULL DTO ========================= */

	public DriverDTO assemble(Driver driver) {
		if (driver == null) {
			return null;
		}

		return new DriverDTO(driver.getId(), driver.getOrgId(), driver.getClientId(),
				driver.getClientId() != null ? enrichName(driver.getClient().getName()) : null,
				enrichName(driver.getName()), enrichName(driver.getFatherName()),

				driver.getGender(), driver.getPhone(), driver.getAlternatePhone(), driver.getEmail(),

				enrichAddress(driver.getAddress()),
				AddressUtil.toAddressSnapshotDTO(driver.getGarageLocation()),
				driver.getExperienceYears(),

				driver.getAdharNumber(), driver.getLicenseNumber(),
				fileAccessTokenService.toAccessUrl(driver.getPic(), driver.getOrgId(), FileAccessCategory.PRIVATE),

				driver.getOwnership(),

				driver.getCreatedAt(), driver.getUpdatedAt(),
				auditActorService.resolve(driver.getCreatedBy()).displayName(),
				auditActorService.resolve(driver.getUpdatedBy()).displayName());
	}

	/* ========================= LIST / PAGE ========================= */

	public Page<DriverListItem> assemble(Page<Driver> page) {
		return page.map(this::toListItem);
	}

	private DriverListItem toListItem(Driver driver) {
		return new DriverListItem(driver.getId(),
				driver.getClient() != null ? enrichName(driver.getClient().getName()) : null,

				enrichName(driver.getName()), driver.getPhone(),

				enrichAddress(driver.getAddress()), driver.getLicenseNumber(),
				fileAccessTokenService.toAccessUrl(driver.getPic(), driver.getOrgId(), FileAccessCategory.PRIVATE),

				driver.getOwnership());
	}

	/* ========================= SUB-MAPPERS ========================= */

	private NameDTO enrichName(Name name) {
		if (name == null) {
			return null;
		}

		return new NameDTO(name.getSalutation(), name.getFirstName(), name.getLastName());
	}

	private DisplayAddressDTO enrichAddress(DisplayAddress address) {
		if (address == null) {
			return null;
		}

		return new DisplayAddressDTO(address.getFormattedAddress(), address.getCity(), address.getState(),
				address.getPincode(), address.getCountryCode());
	}
}

package com.core.services.config;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.config.GarageRequest;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.CityGarage;
import com.core.repositories.CityGarageRepository;
import com.core.util.AddressUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CityGarageService {
	private final CityGarageRepository garageRepo;

	@Transactional
	public CityGarage save(String orgId, GarageRequest request) {
		CityGarage garage = new CityGarage(null, orgId, request.city(),
				AddressUtil.toAddressSnapshot(request.garageLocation()));
		return this.garageRepo.save(garage);
	}

	@Transactional
	public CityGarage update(String orgId, GarageRequest request) {
		CityGarage garage = this.get(orgId, request.city());
		garage.setGarageLocation(AddressUtil.toAddressSnapshot(request.garageLocation()));
		return this.garageRepo.save(garage);
	}

	@Transactional(readOnly = true)
	public CityGarage get(String orgId, String city) {
		return this.garageRepo.findByOrgIdAndCity(orgId, city)
				.orElseThrow(() -> new NotFoundException(ErrorCode.GARAGE_NOT_FOUND, "Garage not found at this city."));
	}

	@Transactional(readOnly = true)
	public List<CityGarage> getList(String orgId) {
		return this.garageRepo.findByOrgId(orgId);
	}
}

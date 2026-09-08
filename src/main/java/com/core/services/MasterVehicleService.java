package com.core.services;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.client.app.VehicleCatalogDTO;
import com.core.dtos.client.app.VehicleValidationResponse;
import com.core.dtos.vehicle.MasterVehicleDTO;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.dtos.client.app.ItineraryInput;
import com.core.location.api.DistanceTimeResult;
import com.core.location.api.LocationService;
import com.core.mapper.VehicleAssembler;
import com.core.mapper.VehicleCatalogAssembler;
import com.core.models.MasterVehicle;
import com.core.models.Package;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.enums.DutyType;
import com.core.models.enums.VehicleStatus;
import com.core.repositories.CityGarageRepository;
import com.core.repositories.MasterVehicleRepository;
import com.core.services.common.FileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MasterVehicleService {
	private final MasterVehicleRepository masterVehicleRepository;
	private final FileService fileService;
	private final PackageService packageService;
	private final VehicleCatalogAssembler catalogAssembler;
	private final LocationService locationService;
	private final CityGarageRepository garageRepository;
	private final VehicleCatalogAssembler vehicleCatalogAssembler;
	private final VehicleAssembler assembler;

	@Transactional
	public MasterVehicleDTO saveVehicle(MasterVehicle vehicle) {
		return assembler.assemble(this.masterVehicleRepository.save(vehicle));
	}

	@Transactional
	public MasterVehicleDTO updateVehicle(MasterVehicle vehicle) {
		MasterVehicle local = this.get(vehicle.getId());
		local.setName(vehicle.getName());
		local.setSeats(vehicle.getSeats());
		local.setBrand(vehicle.getBrand());
		local.setCategory(vehicle.getCategory());
		local.setChauffeurDriven(vehicle.getChauffeurDriven());
		local.setDimension_height(vehicle.getDimension_height());
		local.setDimension_length(vehicle.getDimension_length());
		local.setDimension_width(vehicle.getDimension_width());
		local.setDimension_wheelbase(vehicle.getDimension_wheelbase());
		local.setDoors(vehicle.getDoors());
		local.setFuelConsumption(vehicle.getFuelConsumption());
		local.setFuelSystem(vehicle.getFuelSystem());
		local.setHorsePower(vehicle.getHorsePower());
		local.setModelYear(vehicle.getModelYear());
		local.setPerformance(vehicle.getPerformance());
		local.setPopularity(vehicle.getPopularity());
		local.setRating(vehicle.getRating());
		local.setSlug(vehicle.getSlug());
		local.setTransmissionType(vehicle.getTransmissionType());
		local.setVehicleClass(vehicle.getVehicleClass());
		local.setVehicleColor(vehicle.getVehicleColor());
		local.setStatus(vehicle.getStatus());
		local.setRemarks(vehicle.getRemarks());
		return assembler.assemble(this.masterVehicleRepository.save(local));
	}

	@Transactional
	public void delete(String vehicleId) {
		MasterVehicle vehicle = this.get(vehicleId);
		if (vehicle.getPic() != null) {
			this.fileService.deleteFile(vehicle.getPic());
		}
		this.masterVehicleRepository.deleteById(vehicleId);
	}

	@Transactional(readOnly = true)
	public MasterVehicle get(String vehicleId) {
		return this.masterVehicleRepository.findById(vehicleId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.VEHICLE_NOT_FOUND, "Vehicle not found with this Id."));
	}

	@Transactional(readOnly = true)
	public MasterVehicleDTO getMasterVehicleDTO(String vehicleId) {
		return assembler.assemble(this.get(vehicleId));
	}

	@Transactional(readOnly = true)
	public List<MasterVehicleDTO> findByOrgId(String orgId) {
		return this.masterVehicleRepository.findByOrgId(orgId).stream().map(assembler::assemble).toList();
	}

	@Transactional(readOnly = true)
	public Page<MasterVehicleDTO> getVehiclePage(String orgId, String searchStr, Pageable pageable) {
			if (searchStr == null || searchStr.isBlank()) {
				return masterVehicleRepository.findByOrgId(orgId, pageable).map(assembler::assemble);
			}
			return masterVehicleRepository.findByOrgIdAndSearch(orgId, searchStr, pageable).map(assembler::assemble);
	}

	@Transactional
	public MasterVehicleDTO updatePic(String vehicleId, MultipartFile file) throws IOException {
			MasterVehicle vehicle = this.get(vehicleId);
			if (vehicle.getPic() != null) {
				this.fileService.deleteFile(vehicle.getPic());
			}
			vehicle.setPic(this.fileService.saveDisplayImage(file));
			return assembler.assemble(this.masterVehicleRepository.save(vehicle));
	}

	/*
	 * ===================================================== CLIENT APP METHODS
	 * =====================================================
	 */

	@Transactional(readOnly = true)
	public Page<VehicleCatalogDTO> findTrending(String orgId, String clientId, String location, Pageable pageable) {

		Page<MasterVehicle> vpage = this.masterVehicleRepository.findTrendingByLocation(orgId, location,
				VehicleStatus.PUBLISHED, pageable);

		List<VehicleCatalogDTO> result = vpage.getContent().stream().map(v -> catalogAssembler.assemble(v,
				packageService.getSalesPackagesByLocation(orgId, v.getId(), clientId, location))).toList();

		return new PageImpl<>(result, pageable, vpage.getTotalElements());
	}

	@Transactional(readOnly = true)
	public Page<VehicleCatalogDTO> findExplorerPage(String orgId, String clientId, String location, String searchStr,
			List<String> brands, List<String> categories, Pageable pageable) {

		Page<MasterVehicle> vpage = masterVehicleRepository.findExplorerByLocation(orgId, location,
				VehicleStatus.PUBLISHED, searchStr, brands, categories, pageable);

		List<VehicleCatalogDTO> result = vpage.getContent().stream().map(v -> catalogAssembler.assemble(v,
				packageService.getSalesPackagesByLocation(orgId, v.getId(), clientId, location))).toList();

		return new PageImpl<>(result, pageable, vpage.getTotalElements());
	}

	@Transactional(readOnly = true)
	public Map<String, List<String>> getFilterMeta(String orgId) {

		Map<String, List<String>> filters = new HashMap<>();

		List<String> brands = masterVehicleRepository.findDistinctBrands(orgId, VehicleStatus.PUBLISHED);

		List<String> categories = masterVehicleRepository.findDistinctCategories(orgId, VehicleStatus.PUBLISHED);

		filters.put("brands", brands);
		filters.put("categories", categories);

		return filters;
	}

	@Transactional(readOnly = true)
	public Page<VehicleCatalogDTO> searchByItinerary(String orgId, String clientId, ItineraryInput request,
			Pageable pageable) {
		
		String city = locationService.resolveCity(request.reportingLocation().toAddressSnapshot());

		DutyType effectiveDutyType = resolveEffectiveDutyType(orgId, request, city);

		// Expand duty types for querying
		List<DutyType> dutyTypes = resolveQueryableDutyTypes(effectiveDutyType);

		// 1️⃣ Page vehicles
		Page<MasterVehicle> vehicles = masterVehicleRepository.findVehicleCatalogByLocationAndDuty(orgId, clientId,
				city, dutyTypes, VehicleStatus.PUBLISHED, pageable);

		// 2️⃣ Map vehicles → catalog DTO
		return vehicles.map(vehicle -> {

			// resolve exactly ONE package for this vehicle
			Package pkg = packageService.getPackageByVehicleAndDutyType(orgId, clientId, vehicle.getId(), dutyTypes,
					city);

			return vehicleCatalogAssembler.assemble(vehicle, List.of(pkg));
		});
	}

	@Transactional(readOnly = true)
	public VehicleValidationResponse validateVehicle(String orgId, String clientId, String vehicleId,
			ItineraryInput request) {

		String city = locationService.resolveCity(request.reportingLocation().toAddressSnapshot());
		
		// 1️⃣ Load vehicle
		MasterVehicle vehicle = masterVehicleRepository.findById(vehicleId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.VEHICLE_NOT_FOUND, "Vehicle Not found."));

		if (!vehicle.getStatus().equals(VehicleStatus.PUBLISHED)) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Vehicle is not available for booking.");
		}

		// 2️⃣ Resolve effective duty type
		DutyType effectiveDutyType = resolveEffectiveDutyType(orgId, request, city);

		// 3️⃣ Resolve queryable duty types
		List<DutyType> dutyTypes = resolveQueryableDutyTypes(effectiveDutyType);

		// 4️⃣ Fetch correct package
		Package pkg = packageService.getPackageByVehicleAndDutyType(orgId, clientId, vehicleId, dutyTypes,
				city);

		if (pkg == null) {
			throw new BusinessException(ErrorCode.PACKAGE_NOT_FOUND, "Vehicle is not available for this ride.");
		}

		// 5️⃣ Create warning (if duty type changed)
		String warning = null;

		if (!effectiveDutyType.equals(request.dutyType())) {
			warning = "Duty type adjusted based on distance and time";
		}

		// 6️⃣ Map to DTO
		VehicleCatalogDTO.Package selectedPackage = new VehicleCatalogDTO.Package(pkg.getId(), pkg.getDutyType().name(),
				pkg.getBaseFare(), pkg.getExtraPerKM(), pkg.getExtraPerHS(), pkg.getNightCharge());

		return new VehicleValidationResponse(vehicleId, selectedPackage, warning);
	}

	private DutyType resolveEffectiveDutyType(String orgId, ItineraryInput itinerary, String city) {

		if (itinerary.dutyType().equals(DutyType.TRANSFER)) {

			AddressSnapshot garageLocation = garageRepository.findByOrgIdAndCity(orgId, city)
					.orElseThrow(() -> new BusinessException(ErrorCode.GARAGE_NOT_FOUND, "Currently not available at this location.")).getGarageLocation();

			AddressSnapshot source = itinerary.reportingLocation().toAddressSnapshot();
			AddressSnapshot destination = itinerary.dropLocation().toAddressSnapshot();

			// Distance + time: garage → pickup → drop → garage
			DistanceTimeResult gToPickup = locationService.calculateDistanceAndTime(garageLocation, source);
			DistanceTimeResult pickupToDrop = locationService.calculateDistanceAndTime(source, destination);
			DistanceTimeResult dropToGarage = locationService.calculateDistanceAndTime(destination, garageLocation);

			double totalDistance = gToPickup.distanceKm() + pickupToDrop.distanceKm() + dropToGarage.distanceKm();

			long totalTime = gToPickup.durationSeconds() + pickupToDrop.durationSeconds()
					+ dropToGarage.durationSeconds();

			/*
			 * P0 financial-integrity guard -- this reclassification changes
			 * which Package (and therefore which price) the customer is
			 * shown and ultimately books, so it must not fire on
			 * unreliable distance data. result.estimated() is true for a
			 * haversine straight-line guess (FallbackGeoProvider) or a
			 * stale, past-TTL cache entry served only because live
			 * computation just failed (RouteCacheService) -- neither is
			 * authoritative enough to override the customer's requested
			 * duty type. When any leg is estimated, this simply does not
			 * reclassify: the itinerary's own requested TRANSFER (and the
			 * airport-based sub-type below) stands, exactly as if the
			 * threshold check had never run -- never a fabricated
			 * LOCAL/TRANSFER decision built on unreliable data.
			 */
			boolean allLegsTrustworthy =
					!gToPickup.estimated() && !pickupToDrop.estimated() && !dropToGarage.estimated();

			if (!allLegsTrustworthy) {
				log.warn(
						"Duty-type distance classification for org {} used estimated route data "
								+ "(providers: {}, {}, {}) -- skipping the LOCAL reclassification rather than "
								+ "risking an incorrect package/price on unreliable data",
						orgId, gToPickup.provider(), pickupToDrop.provider(), dropToGarage.provider());
			}

			// Convert to LOCAL if short trip -- only ever decided from trustworthy data.
			if (allLegsTrustworthy && (totalDistance > 40 || totalTime > 14400)) {
				return DutyType.LOCAL;
			}

			Boolean isAirportDrop = locationService.isAirport(itinerary.dropLocation().toAddressSnapshot());
			Boolean isAirportPickup = locationService.isAirport(itinerary.reportingLocation().toAddressSnapshot());
			if (isAirportDrop && isAirportPickup) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Both locations are airport.");
			} else if (isAirportDrop) {
				return DutyType.AIRPORT_DROP;
			} else if (isAirportPickup) {
				return DutyType.AIRPORT_PICKUP;
			}
			return DutyType.TRANSFER;
		} else if (itinerary.dutyType().equals(DutyType.LOCAL)) {
			return DutyType.LOCAL;
		} else if (itinerary.dutyType().equals(DutyType.OUTSTATION)) {
			return DutyType.OUTSTATION;
		}
		throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Duty type is required.");
	}

	private List<DutyType> resolveQueryableDutyTypes(DutyType effectiveDutyType) {

		return switch (effectiveDutyType) {
		case AIRPORT_PICKUP -> List.of(DutyType.TRANSFER, DutyType.AIRPORT_PICKUP, DutyType.AIRPORT_DROP);

		case AIRPORT_DROP -> List.of(DutyType.TRANSFER, DutyType.AIRPORT_DROP, DutyType.AIRPORT_PICKUP);

		case TRANSFER -> List.of(DutyType.TRANSFER, DutyType.AIRPORT_PICKUP, DutyType.AIRPORT_DROP);

		default -> List.of(effectiveDutyType);
		};
	}

}

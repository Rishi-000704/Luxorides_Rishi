package com.core.controllers.client.app;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.core.dtos.client.app.ItineraryInput;
import com.core.dtos.client.app.VehicleCatalogDTO;
import com.core.dtos.client.app.VehicleValidationResponse;
import com.core.models.Client;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;
import com.core.services.MasterVehicleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/client/app/catalog")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class VehicleCatalogController {

	private final MasterVehicleService service;
	private final SecurityContextUtil security;
	private final ClientService clientService;

	/*
	 * ===================================================== TRENDING VEHICLES (MAX
	 * 10) =====================================================
	 */

	@GetMapping("/trending")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Page<VehicleCatalogDTO>> trending(@RequestParam String location,
			@PageableDefault(size = 6) Pageable pageable) {
		Client client = clientService.findByUserId(security.userId());
		return ResponseEntity.ok(service.findTrending(security.orgId(), client.getId(), location, pageable));
	}

	/*
	 * ===================================================== EXPLORER PAGE (FILTERED
	 * + PAGED) =====================================================
	 */

	@GetMapping("/explorer")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Page<VehicleCatalogDTO>> explorer(@RequestParam String location,
			@RequestParam(required = false) String searchStr, @RequestParam(required = false) List<String> brands,
			@RequestParam(required = false) List<String> categories, @PageableDefault(size = 12) Pageable pageable) {
		Client client = clientService.findByUserId(security.userId());

		return ResponseEntity.ok(service.findExplorerPage(security.orgId(), client.getId(), location, searchStr, brands,
				categories, pageable));
	}

	/*
	 * ===================================================== FILTER METADATA (BRANDS
	 * + CATEGORIES) =====================================================
	 */

	@GetMapping("/filters")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, List<String>>> filters() {
		String orgId = security.orgId();

		return ResponseEntity.ok(service.getFilterMeta(orgId));
	}

	@PostMapping("/vehicles/search-by-itinerary")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Page<VehicleCatalogDTO>> searchByItinerary(
	        @RequestBody @Valid ItineraryInput request,
	        @PageableDefault(size = 10) Pageable pageable) {

	    Client client = clientService.findByUserId(security.userId());

	    return ResponseEntity.ok(
	        service.searchByItinerary(
	            security.orgId(),
	            client.getId(),
	            request,
	            pageable
	        )
	    );
	}
	
	@PostMapping("/{vehicleId}/validate")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<VehicleValidationResponse> validate(
	        @PathVariable String vehicleId,
	        @RequestBody @Valid ItineraryInput request) {

	    Client client = clientService.findByUserId(security.userId());

	    return ResponseEntity.ok(
	        service.validateVehicle(
	            security.orgId(),
	            client.getId(),
	            vehicleId,
	            request
	        )
	    );
	}
}

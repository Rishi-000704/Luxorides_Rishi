package com.core.controllers;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driver.DriverDTO;
import com.core.dtos.driver.DriverListItem;
import com.core.dtos.driver.DriverOpsRatingRequest;
import com.core.dtos.driver.DriverOpsRatingResponse;
import com.core.dtos.driver.DriverRatingSummaryResponse;
import com.core.dtos.driverduty.DocumentReviewRequest;
import com.core.dtos.driverduty.DriverDocumentReviewResponse;
import com.core.mapper.DriverAssembler;
import com.core.models.Driver;
import com.core.security.SecurityContextUtil;
import com.core.services.DriverDocumentService;
import com.core.services.DriverRatingService;
import com.core.services.DriverService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/employee/drivers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class DriverController {

	private final DriverService driverService;
	private final DriverDocumentService driverDocumentService;
	private final DriverRatingService driverRatingService;
	private final SecurityContextUtil security;
	private final DriverAssembler assembler;

	@PostMapping
	@PreAuthorize("hasAuthority('DRIVER_ADD')")
	public DriverDTO createDriver(@RequestBody Driver driver) throws Exception {
		return assembler.assemble(driverService.saveDriver(driver, security.orgId()));
	}

	@PutMapping("/{driverId}")
	@PreAuthorize("hasAuthority('DRIVER_EDIT')")
	public DriverDTO updateDriver(@PathVariable String driverId, @RequestBody Driver driver) {
		driver.setId(driverId);
		return assembler.assemble(driverService.updateDriver(driver, security.orgId()));
	}

	@DeleteMapping("/{driverId}")
	@PreAuthorize("hasAuthority('DRIVER_DELETE')")
	public void deleteDriver(@PathVariable String driverId) {
		driverService.deleteDriver(driverId, security.orgId());
	}

	@GetMapping("/{driverId}")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public DriverDTO getDriver(@PathVariable String driverId) {
		return assembler.assemble(driverService.getDriver(driverId, security.orgId()));
	}

	@GetMapping("/client/{clientId}")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public List<DriverDTO> getDriversByClient(@PathVariable String clientId) {
		return driverService.getDriversByClient(clientId, security.orgId()).stream().map(assembler::assemble).toList();
	}

	@GetMapping("/self")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public List<DriverDTO> getSelfDrivers() {
		return driverService.getSelfDrivers(security.orgId()).stream().map(assembler::assemble).toList();
	}

	@GetMapping
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public Page<DriverListItem> getDrivers(@RequestParam(required = false) String search, Pageable pageable) {
		return assembler.assemble(driverService.getDriverPage(security.orgId(), search, pageable));
	}

	@PostMapping("/{driverId}/pic")
	@PreAuthorize("hasAuthority('DRIVER_EDIT')")
	public DriverDTO updateDriverPic(@PathVariable String driverId, @RequestParam MultipartFile file) {
		return assembler.assemble(driverService.updatePic(driverId, security.orgId(), file));
	}

	// KYC document review -- the safety gate the app didn't have before:
	// see DriverDocumentService#areRequiredDocumentsVerified, enforced at
	// duty accept/start so a vehicle can't leave the garage on an
	// unverified driver.
	@GetMapping("/{driverId}/documents")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public List<DriverDocumentReviewResponse> getDriverDocuments(@PathVariable String driverId) {
		return driverDocumentService.listForReview(security.orgId(), driverId);
	}

	@PutMapping("/{driverId}/documents/{documentType}/review")
	@PreAuthorize("hasAuthority('DRIVER_EDIT')")
	public DriverDocumentReviewResponse reviewDriverDocument(
			@PathVariable String driverId,
			@PathVariable String documentType,
			@Valid @RequestBody DocumentReviewRequest request
	) {
		return driverDocumentService.reviewDocument(security.orgId(), driverId, documentType, security.userId(), request);
	}

	// Ops's own current assessment of this driver -- separate from the
	// customer's per-duty TripRating. Averaged together for the driver-facing
	// summary (see DriverRatingService, DriverAppController's /rating).
	@GetMapping("/{driverId}/ops-rating")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public DriverOpsRatingResponse getOpsRating(@PathVariable String driverId) {
		return driverRatingService.getOpsRating(security.orgId(), driverId);
	}

	@PutMapping("/{driverId}/ops-rating")
	@PreAuthorize("hasAuthority('DRIVER_EDIT')")
	public DriverOpsRatingResponse setOpsRating(@PathVariable String driverId, @Valid @RequestBody DriverOpsRatingRequest request) {
		return driverRatingService.setOpsRating(security.orgId(), driverId, request);
	}

	@GetMapping("/{driverId}/rating")
	@PreAuthorize("hasAuthority('DRIVER_VIEW')")
	public DriverRatingSummaryResponse getRatingSummary(@PathVariable String driverId) {
		return driverRatingService.getRatingSummary(security.orgId(), driverId);
	}
}

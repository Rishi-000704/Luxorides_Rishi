package com.core.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import com.core.dtos.driverduty.DriverAppDutyTokenResponse;
import com.core.dtos.driverduty.DriverDutyAcceptanceResponse;
import com.core.dtos.driverduty.DriverDutyDeclineRequest;
import com.core.dtos.driverduty.DriverDutyDeclineResponse;
import com.core.dtos.driverduty.DutySummaryForDriverDTO;
import com.core.dtos.driverduty.VehicleInspectionRequest;
import com.core.dtos.driverduty.VehicleInspectionResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.DriverAppService;
import com.core.services.VehicleInspectionService;

import lombok.RequiredArgsConstructor;

/*
 * Authenticated driver self-service dashboard API. Mirrors the customer app's
 * /client/app/** naming. Distinct from the public, token-authenticated
 * /driver-api/duty/{token}/** family (ExternalDriverDutyController), which remains
 * untouched and still does the actual duty execution.
 */
@RestController
@RequestMapping("/driver/app")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverAppController {

	private final DriverAppService driverAppService;
	private final VehicleInspectionService vehicleInspectionService;
	private final SecurityContextUtil security;

	@GetMapping("/duties/active")
	public Page<DutySummaryForDriverDTO> getActiveDuties(Pageable pageable) {
		return driverAppService.getActiveDuties(security.orgId(), security.userId(), pageable);
	}

	@GetMapping("/duties/history")
	public Page<DutySummaryForDriverDTO> getDutyHistory(Pageable pageable) {
		return driverAppService.getDutyHistory(security.orgId(), security.userId(), pageable);
	}

	@GetMapping("/duties/{dutyId}")
	public DutySummaryForDriverDTO getDuty(@PathVariable String dutyId) {
		return driverAppService.getDuty(security.orgId(), security.userId(), dutyId);
	}

	@PostMapping("/duties/{dutyId}/token")
	public DriverAppDutyTokenResponse issueExecutionToken(@PathVariable String dutyId) {
		return driverAppService.issueExecutionToken(security.orgId(), security.userId(), dutyId);
	}

	@PostMapping("/duties/{dutyId}/accept")
	public DriverDutyAcceptanceResponse acceptDuty(@PathVariable String dutyId) {
		return driverAppService.acceptDuty(security.orgId(), security.userId(), dutyId);
	}

	@PostMapping("/duties/{dutyId}/decline")
	public DriverDutyDeclineResponse declineDuty(
			@PathVariable String dutyId,
			@RequestBody DriverDutyDeclineRequest payload
	) {
		return driverAppService.declineDuty(security.orgId(), security.userId(), dutyId, payload);
	}

	@PostMapping(
			value = "/duties/{dutyId}/inspection",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	public VehicleInspectionResponse submitInspection(
			@PathVariable String dutyId,
			@RequestPart("payload") VehicleInspectionRequest payload,
			@RequestPart(value = "exteriorFront", required = false) MultipartFile exteriorFront,
			@RequestPart(value = "exteriorBack", required = false) MultipartFile exteriorBack,
			@RequestPart(value = "exteriorLeft", required = false) MultipartFile exteriorLeft,
			@RequestPart(value = "exteriorRight", required = false) MultipartFile exteriorRight,
			@RequestPart(value = "interiorDashboard", required = false) MultipartFile interiorDashboard,
			@RequestPart(value = "interiorFrontSeats", required = false) MultipartFile interiorFrontSeats,
			@RequestPart(value = "interiorBackSeats", required = false) MultipartFile interiorBackSeats,
			@RequestPart(value = "interiorBootSpace", required = false) MultipartFile interiorBootSpace,
			@RequestPart(value = "uniformSelfie", required = false) MultipartFile uniformSelfie
	) throws IOException {
		return vehicleInspectionService.submitInspection(
				security.orgId(),
				security.userId(),
				dutyId,
				payload,
				exteriorFront,
				exteriorBack,
				exteriorLeft,
				exteriorRight,
				interiorDashboard,
				interiorFrontSeats,
				interiorBackSeats,
				interiorBootSpace,
				uniformSelfie
		);
	}
}

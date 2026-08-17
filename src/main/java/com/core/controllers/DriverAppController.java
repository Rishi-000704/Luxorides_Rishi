package com.core.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.driverduty.DriverAppDutyTokenResponse;
import com.core.dtos.driverduty.DutySummaryForDriverDTO;
import com.core.security.SecurityContextUtil;
import com.core.services.DriverAppService;

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
}

package com.core.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.driverduty.DriverDutyLinkResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.ExternalDriverDutyService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/booking/employee")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeDriverDutyLinkController {

	private final ExternalDriverDutyService externalDriverDutyService;
	private final SecurityContextUtil security;

	@PostMapping("/{bookingId}/duties/{dutyId}/driver-link")
	@PreAuthorize("hasAuthority('BOOKING_ALLOT_DUTY')")
	public DriverDutyLinkResponse generateDriverDutyLink(
		@PathVariable String bookingId,
		@PathVariable String dutyId
	) {
		return externalDriverDutyService.generateDriverDutyLink(
			bookingId,
			dutyId,
			security.orgId()
		);
	}
}
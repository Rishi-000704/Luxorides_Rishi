package com.core.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.driverduty.DriverDutySubmissionViewResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.EmployeeDriverDutySubmissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/booking/employee")
@CrossOrigin("*")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeDriverDutySubmissionController {

	private final EmployeeDriverDutySubmissionService submissionService;
	private final SecurityContextUtil security;

	@GetMapping("/{bookingId}/duties/{dutyId}/driver-submission")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public DriverDutySubmissionViewResponse getDriverDutySubmission(
			@PathVariable String bookingId,
			@PathVariable String dutyId
	) {
		return submissionService.getDutySubmissionView(
				bookingId,
				dutyId,
				security.orgId()
		);
	}
}
package com.core.controllers;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.fraud.FraudSignalResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.FraudSignalService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/fraud-signals")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class FraudSignalController {

	private final FraudSignalService fraudSignalService;
	private final SecurityContextUtil security;

	@GetMapping
	@PreAuthorize("hasAuthority('FRAUD_SIGNAL_VIEW')")
	public List<FraudSignalResponse> list() {
		return fraudSignalService.list(security.orgId());
	}

	@PostMapping("/{id}/dismiss")
	@PreAuthorize("hasAuthority('FRAUD_SIGNAL_REVIEW')")
	public FraudSignalResponse dismiss(@PathVariable String id) {
		return fraudSignalService.review(id, security.orgId(), security.userId(), true);
	}

	@PostMapping("/{id}/mark-reviewed")
	@PreAuthorize("hasAuthority('FRAUD_SIGNAL_REVIEW')")
	public FraudSignalResponse markReviewed(@PathVariable String id) {
		return fraudSignalService.review(id, security.orgId(), security.userId(), false);
	}
}

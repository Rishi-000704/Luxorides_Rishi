package com.core.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.pricing.DynamicPricingResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.DynamicPricingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/fleet/pricing")
@PreAuthorize("hasRole('EMPLOYEE') and hasAuthority('DYNAMIC_PRICING_CONFIG_VIEW')")
@RequiredArgsConstructor
public class DynamicPricingController {

	private final DynamicPricingService dynamicPricingService;
	private final SecurityContextUtil security;

	@GetMapping("/dynamic-multiplier")
	public DynamicPricingResponse getMultiplier() {
		return dynamicPricingService.computeMultiplier(security.orgId());
	}
}

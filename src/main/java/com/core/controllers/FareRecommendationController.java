package com.core.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.pricing.FareRecommendationResponse;
import com.core.models.enums.DutyType;
import com.core.security.SecurityContextUtil;
import com.core.services.FareRecommendationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/fleet/pricing")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class FareRecommendationController {

	private final FareRecommendationService fareRecommendationService;
	private final SecurityContextUtil security;

	@GetMapping("/fare-recommendation")
	public FareRecommendationResponse recommend(
			@RequestParam String masterVehicleId,
			@RequestParam DutyType dutyType,
			@RequestParam double distanceKm) {
		return fareRecommendationService.recommend(security.orgId(), masterVehicleId, dutyType, distanceKm);
	}
}

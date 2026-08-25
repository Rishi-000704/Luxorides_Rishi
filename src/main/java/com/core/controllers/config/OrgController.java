package com.core.controllers.config;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.config.CancellationPolicyUpdateRequest;
import com.core.dtos.config.DynamicPricingConfigRequest;
import com.core.dtos.config.OrganizationUpdateRequest;
import com.core.models.Org;
import com.core.security.SecurityContextUtil;
import com.core.services.config.OrgService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/config/org")
@RestController
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class OrgController {
	private final OrgService orgService;
	private final SecurityContextUtil security;

	@GetMapping
	@PreAuthorize("hasAuthority('ORG_VIEW')")
	public Org getOrg() {
		return this.orgService.getOrg(security.orgId());
	}

	@PutMapping
	@PreAuthorize("hasAuthority('ORG_EDIT')")
	public Org updateOrg(@RequestBody OrganizationUpdateRequest request) {
		return this.orgService.updateOrg(security.orgId(), request);
	}

	@PutMapping("/cancellation-policy")
	@PreAuthorize("hasAuthority('ORG_EDIT')")
	public Org updateCancellationPolicy(@RequestBody CancellationPolicyUpdateRequest request) {
		return this.orgService.updateCancellationPolicy(security.orgId(), request);
	}

	@PutMapping("/dynamic-pricing")
	@PreAuthorize("hasAuthority('DYNAMIC_PRICING_CONFIG_EDIT')")
	public Org updateDynamicPricingConfig(@RequestBody DynamicPricingConfigRequest request) {
		return this.orgService.updateDynamicPricingConfig(security.orgId(), request);
	}

}

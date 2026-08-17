package com.core.services.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.config.OrganizationUpdateRequest;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Org;
import com.core.repositories.OrgRepository;
import com.core.util.AddressUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrgService {
	private final OrgRepository orgRepo;

	@Transactional(readOnly = true)
	public Org getOrg(String orgId) {
		return this.orgRepo.findByOrgId(orgId).orElseThrow(
				() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND, "Organization not found with this id."));
	}

	@Transactional
	public Org updateOrg(String orgId, OrganizationUpdateRequest request) {
		Org org = this.getOrg(orgId);
		org.setOrgName(request.orgName());
		org.setAddress(AddressUtil.toDisplayAddress(request.address()));
		org.setAlternatePhone(request.alternatePhone());
		org.setCin(request.cin());
		org.setEmail(request.email());
		org.setGstin(request.gstin());
		org.setPan(request.pan());
		org.setPhone(request.phone());
		org.setWebsiteLink(request.websiteLink());
		return this.orgRepo.save(org);
	}

}

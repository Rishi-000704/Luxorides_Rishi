package com.core.controllers.config;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.config.BillingEntityRequest;
import com.core.models.OrgBillingEntity;
import com.core.security.SecurityContextUtil;
import com.core.services.config.OrgBillingEntityService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/config/billing-entities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class OrgBillingEntityController {

	private final OrgBillingEntityService service;
	private final SecurityContextUtil security;

	/*
	 * Existing JSON create API.
	 */
	@PostMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE
	)
	@PreAuthorize(
			"hasAuthority('ORG_BILLING_ENTITY_ADD')"
	)
	public OrgBillingEntity add(
			@RequestBody BillingEntityRequest request
	) {

		return service.add(
				security.orgId(),
				request
		);
	}

	/*
	 * Create with optional logo.
	 *
	 * Multipart contract:
	 * data -> application/json
	 * logo -> JPEG/PNG, optional
	 */
	@PostMapping(
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	@PreAuthorize(
			"hasAuthority('ORG_BILLING_ENTITY_ADD')"
	)
	public OrgBillingEntity addWithLogo(
			@RequestPart("data")
			BillingEntityRequest request,

			@RequestPart(
					value = "logo",
					required = false
			)
			MultipartFile logo
	) {

		return service.add(
				security.orgId(),
				request,
				logo
		);
	}

	/*
	 * Existing JSON edit API.
	 */
	@PutMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE
	)
	@PreAuthorize(
			"hasAuthority('ORG_BILLING_ENTITY_EDIT')"
	)
	public OrgBillingEntity update(
			@RequestBody BillingEntityRequest request
	) {

		return service.update(
				security.orgId(),
				request
		);
	}

	/*
	 * Edit with optional replacement logo.
	 */
	@PutMapping(
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	@PreAuthorize(
			"hasAuthority('ORG_BILLING_ENTITY_EDIT')"
	)
	public OrgBillingEntity updateWithLogo(
			@RequestPart("data")
			BillingEntityRequest request,

			@RequestPart(
					value = "logo",
					required = false
			)
			MultipartFile logo
	) {

		return service.update(
				security.orgId(),
				request,
				logo
		);
	}

	@GetMapping("/{id}")
	@PreAuthorize(
			"hasAuthority('ORG_BILLING_ENTITY_VIEW')"
	)
	public OrgBillingEntity get(
			@PathVariable String id
	) {

		return service.get(
				security.orgId(),
				id
		);
	}

	@GetMapping
	@PreAuthorize(
			"hasAuthority('ORG_BILLING_ENTITY_VIEW')"
	)
	public List<OrgBillingEntity> list() {

		return service.getList(
				security.orgId()
		);
	}
}
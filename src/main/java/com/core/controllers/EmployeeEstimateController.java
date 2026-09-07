package com.core.controllers;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.common.PageResult;
import com.core.dtos.estimate.EstimateDTO;
import com.core.dtos.estimate.EstimateEntryForm;
import com.core.dtos.estimate.EstimateForm;
import com.core.dtos.estimate.EstimateLinkResponse;
import com.core.dtos.estimate.EstimateListItem;
import com.core.mapper.EstimateAssembler;
import com.core.models.enums.EstimateStatus;
import com.core.security.SecurityContextUtil;
import com.core.services.EstimateService;
import com.core.services.EstimateService.EstimateData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/booking/employee/estimates")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeEstimateController {

	private final EstimateService estimateService;
	private final EstimateAssembler estimateAssembler;
	private final SecurityContextUtil security;

	@PostMapping
	@PreAuthorize("hasAuthority('BOOKING_ADD')")
	public EstimateDTO create(
			@RequestBody EstimateForm form) {

		return estimateAssembler.assemble(
				estimateService.create(
						form,
						security.orgId()));
	}

	@PostMapping("/update")
	@PreAuthorize("hasAuthority('BOOKING_EDIT')")
	public EstimateDTO update(
			@RequestBody EstimateForm form) {

		return estimateAssembler.assemble(
				estimateService.update(
						form,
						security.orgId()));
	}

	@PostMapping("/{estimateId}/entries")
	@PreAuthorize("hasAuthority('BOOKING_EDIT')")
	public EstimateDTO addEntry(
			@PathVariable String estimateId,
			@RequestBody EstimateEntryForm form) {

		return estimateAssembler.assemble(
				estimateService.addEntry(
						estimateId,
						form,
						security.orgId()));
	}

	@PostMapping("/{estimateId}/entries/update")
	@PreAuthorize("hasAuthority('BOOKING_EDIT')")
	public EstimateDTO updateEntry(
			@PathVariable String estimateId,
			@RequestBody EstimateEntryForm form) {

		return estimateAssembler.assemble(
				estimateService.updateEntry(
						estimateId,
						form,
						security.orgId()));
	}

	@PostMapping("/{estimateId}/entries/{estimateEntryId}/delete")
	@PreAuthorize("hasAuthority('BOOKING_EDIT')")
	public EstimateDTO deleteEntry(
			@PathVariable String estimateId,
			@PathVariable String estimateEntryId) {

		return estimateAssembler.assemble(
				estimateService.deleteEntry(
						estimateId,
						estimateEntryId,
						security.orgId()));
	}

	@GetMapping("/{estimateId}")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public EstimateDTO get(
			@PathVariable String estimateId) {

		EstimateData data = estimateService.getEstimate(
				estimateId,
				security.orgId());

		return estimateAssembler.assembleEmployeeDetail(
				data.estimate(),
				data.payments());
	}

	@PostMapping("/page")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public PageResult<EstimateListItem> page(
			@RequestParam(required = false)
			EstimateStatus status,

			@RequestParam(defaultValue = "")
			String searchstr,

			@RequestParam(defaultValue = "0")
			int page,

			@RequestParam(defaultValue = "10")
			int size,

			@RequestParam(defaultValue = "createdAt")
			String sortBy,

			@RequestParam(defaultValue = "DESC")
			Sort.Direction direction) {

		Pageable pageable = PageRequest.of(
				page,
				size,
				Sort.by(direction, sortBy));

		return estimateAssembler.assemble(
				estimateService.getPage(
						security.orgId(),
						status,
						searchstr.trim(),
						pageable));
	}

	@PostMapping("/{estimateId}/client-link")
	@PreAuthorize("hasAuthority('BOOKING_EDIT')")
	public EstimateLinkResponse createClientLink(
			@PathVariable String estimateId) {

		return estimateService.createClientLink(
				estimateId,
				security.orgId());
	}

	@GetMapping("/{estimateId}/client-link")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public EstimateLinkResponse getClientLink(
			@PathVariable String estimateId) {

		return estimateService.getClientLinkStatus(
				estimateId,
				security.orgId());
	}

	@PostMapping("/{estimateId}/client-link/revoke")
	@PreAuthorize("hasAuthority('BOOKING_EDIT')")
	public ResponseEntity<Void> revokeClientLink(
			@PathVariable String estimateId) {

		estimateService.revokeClientLink(
				estimateId,
				security.orgId());

		return ResponseEntity.ok().build();
	}
}
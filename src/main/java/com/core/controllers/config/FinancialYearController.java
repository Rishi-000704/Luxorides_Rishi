package com.core.controllers.config;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.core.dtos.config.FinancialYearRequest;
import com.core.models.FinancialYear;
import com.core.security.SecurityContextUtil;
import com.core.services.config.FinancialYearService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/config/financial-years")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class FinancialYearController {

	private final FinancialYearService service;
	private final SecurityContextUtil security;

	@PostMapping
	@PreAuthorize("hasAuthority('FINANCIAL_YEAR_ADD')")
	public FinancialYear create(@RequestBody FinancialYearRequest request) {
		return this.service.save(security.orgId(), request);
	}

	@PutMapping
	@PreAuthorize("hasAuthority('FINANCIAL_YEAR_EDIT')")
	public FinancialYear update(@RequestBody FinancialYearRequest request) {
		return this.service.update(security.orgId(), request);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('FINANCIAL_YEAR_VIEW')")
	public FinancialYear get(@PathVariable String id) {
		return this.service.get(security.orgId(), id);
	}

	@GetMapping
	@PreAuthorize("hasAuthority('FINANCIAL_YEAR_VIEW')")
	public List<FinancialYear> list() {
		return this.service.getAll(security.orgId());
	}

	@GetMapping("/current")
	@PreAuthorize("hasAuthority('FINANCIAL_YEAR_VIEW')")
	public FinancialYear current() {
		return this.service.getCurrentFY(security.orgId(), null);
	}
}
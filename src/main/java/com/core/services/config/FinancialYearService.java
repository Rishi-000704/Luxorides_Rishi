package com.core.services.config;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.config.FinancialYearRequest;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.FinancialYear;
import com.core.repositories.FinancialYearRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinancialYearService {
	private final FinancialYearRepository fyrepo;

	@Transactional
	public FinancialYear save(String orgId, FinancialYearRequest request) {
		FinancialYear year = new FinancialYear();
		year.setEndDate(request.endDate());
		year.setInvoiceCounter(request.invoiceCounter());
		year.setInvoicePrefix(request.invoicePrefix());
		year.setOrgBillingEntityId(request.orgBillingEntityId());
		year.setOrgId(orgId);
		year.setStartDate(request.startDate());
		return this.fyrepo.save(year);
	}

	@Transactional
	public FinancialYear update(String orgId, FinancialYearRequest request) {
		FinancialYear year = this.get(orgId, request.id());
		year.setEndDate(request.endDate());
		year.setInvoiceCounter(request.invoiceCounter());
		year.setInvoicePrefix(request.invoicePrefix());
		year.setOrgBillingEntityId(request.orgBillingEntityId());
		year.setOrgId(orgId);
		year.setStartDate(request.startDate());
		return this.fyrepo.save(year);
	}

	@Transactional(readOnly = true)
	public FinancialYear get(String orgId, String financialYearId) {
		return this.fyrepo.findById(financialYearId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.FINANCIAL_YEAR_NOT_FOUND,
						"Financial year not found with this id."));
	}

	@Transactional(readOnly = true)
	public FinancialYear getCurrentFY(String orgId, String companyId) {
		return this.fyrepo.findCurrentFinancialYear(orgId, companyId);
	}

	@Transactional(readOnly = true)
	public List<FinancialYear> getAll(String orgId) {
		return this.fyrepo.findByOrgId(orgId);
	}

}

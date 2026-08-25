package com.core.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.expense.ExpenseRequest;
import com.core.dtos.expense.ExpenseResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.Expense;
import com.core.repositories.ExpenseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

	private final ExpenseRepository expenseRepository;

	@Transactional
	public ExpenseResponse create(String orgId, ExpenseRequest request) {
		validate(request);

		Expense expense = new Expense();
		expense.setOrgId(orgId);
		applyRequest(expense, request);

		return toResponse(expenseRepository.save(expense));
	}

	@Transactional
	public ExpenseResponse update(String id, String orgId, ExpenseRequest request) {
		validate(request);

		Expense expense = expenseRepository.findByIdAndOrgId(id, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Expense not found"));

		applyRequest(expense, request);

		return toResponse(expenseRepository.save(expense));
	}

	@Transactional
	public void delete(String id, String orgId) {
		Expense expense = expenseRepository.findByIdAndOrgId(id, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Expense not found"));

		expenseRepository.delete(expense);
	}

	@Transactional(readOnly = true)
	public List<ExpenseResponse> list(String orgId) {
		return expenseRepository.findByOrgIdOrderByIncurredAtDesc(orgId).stream().map(this::toResponse).toList();
	}

	private void validate(ExpenseRequest request) {
		if (request.category() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category is required");
		}

		if (request.amount() == null || request.amount().signum() <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Amount must be greater than zero");
		}

		if (request.incurredAt() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Incurred date is required");
		}
	}

	private void applyRequest(Expense expense, ExpenseRequest request) {
		expense.setCategory(request.category());
		expense.setAmount(request.amount());
		expense.setIncurredAt(request.incurredAt());
		expense.setFleetVehicleId(request.fleetVehicleId());
		expense.setDriverId(request.driverId());
		expense.setRemarks(request.remarks());
	}

	private ExpenseResponse toResponse(Expense e) {
		return new ExpenseResponse(e.getId(), e.getCategory(), e.getAmount(), e.getIncurredAt(),
				e.getFleetVehicleId(), e.getDriverId(), e.getRemarks(), e.getCreatedAt());
	}
}

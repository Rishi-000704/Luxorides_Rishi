package com.core.controllers;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.expense.ExpenseRequest;
import com.core.dtos.expense.ExpenseResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.ExpenseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/expense")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class ExpenseController {

	private final ExpenseService expenseService;
	private final SecurityContextUtil security;

	@GetMapping
	@PreAuthorize("hasAuthority('EXPENSE_VIEW')")
	public List<ExpenseResponse> list() {
		return expenseService.list(security.orgId());
	}

	@PostMapping
	@PreAuthorize("hasAuthority('EXPENSE_ADD')")
	public ExpenseResponse create(@RequestBody ExpenseRequest request) {
		return expenseService.create(security.orgId(), request);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('EXPENSE_EDIT')")
	public ExpenseResponse update(@PathVariable String id, @RequestBody ExpenseRequest request) {
		return expenseService.update(id, security.orgId(), request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('EXPENSE_DELETE')")
	public void delete(@PathVariable String id) {
		expenseService.delete(id, security.orgId());
	}
}

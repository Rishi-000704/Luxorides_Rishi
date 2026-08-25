package com.core.controllers;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.dispatch.DispatchSuggestionResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.DispatchSuggestionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/booking/employee/duties")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class DispatchSuggestionController {

	private final DispatchSuggestionService dispatchSuggestionService;
	private final SecurityContextUtil security;

	@GetMapping("/{dutyId}/dispatch-suggestions")
	@PreAuthorize("hasAuthority('DISPATCH_SUGGESTION_VIEW')")
	public DispatchSuggestionResponse suggestions(@PathVariable String dutyId) {
		return dispatchSuggestionService.suggest(dutyId, security.orgId());
	}

	@GetMapping("/dispatch-suggestions/all")
	@PreAuthorize("hasAuthority('DISPATCH_SUGGESTION_VIEW')")
	public Map<String, DispatchSuggestionResponse> suggestionsForAllPending() {
		return dispatchSuggestionService.suggestForAllPendingDuties(security.orgId());
	}
}

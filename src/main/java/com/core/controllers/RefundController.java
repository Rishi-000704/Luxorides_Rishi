package com.core.controllers;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.models.RefundRequest;
import com.core.security.SecurityContextUtil;
import com.core.services.RefundRequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/refund-requests")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class RefundController {

	private final RefundRequestService refundRequestService;
	private final SecurityContextUtil security;

	@GetMapping
	@PreAuthorize("hasAuthority('REFUND_VIEW')")
	public List<RefundRequest> list() {
		return refundRequestService.list(security.orgId());
	}

	public record RejectRequest(String reason) {
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasAuthority('REFUND_APPROVE')")
	public RefundRequest approve(@PathVariable String id) {
		return refundRequestService.approve(id, security.orgId(), security.userId());
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasAuthority('REFUND_APPROVE')")
	public RefundRequest reject(@PathVariable String id, @RequestBody RejectRequest request) {
		return refundRequestService.reject(id, security.orgId(), security.userId(), request.reason());
	}
}

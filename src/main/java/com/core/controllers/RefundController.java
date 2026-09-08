package com.core.controllers;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.payment.RefundOpsListItem;
import com.core.models.RefundRequest;
import com.core.models.enums.RefundRequestStatus;
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

	/*
	 * Ops recovery view: org-scoped, paginated, sorted predictably (defaults
	 * to newest-first), optionally filtered by status -- so an operator can
	 * pull up exactly the COMPLETED_NEEDS_VERIFICATION queue. Follows the
	 * same page/size/sortBy/direction convention as the existing employee
	 * paginated list endpoints (see EmployeeInvoiceController).
	 */
	@GetMapping("/page")
	@PreAuthorize("hasAuthority('REFUND_VIEW')")
	public Page<RefundOpsListItem> getPage(
			@RequestParam(required = false) RefundRequestStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "DESC") Sort.Direction direction) {

		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
		return refundRequestService.getOpsPage(security.orgId(), status, pageable);
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

	/*
	 * The COMPLETED_NEEDS_VERIFICATION recovery action. Deliberately not a
	 * "refund again" endpoint -- it only ever queries Razorpay and, depending
	 * on what that proves, either fixes local state to COMPLETED or reopens
	 * the request to PENDING_REVIEW so a genuine retry still goes through the
	 * existing approve() endpoint's own authorization/idempotency guards.
	 * Requires REFUND_APPROVE (the same authority approve/reject already
	 * require), not REFUND_VIEW: this can change financial state.
	 */
	@PostMapping("/{id}/verify-recovery")
	@PreAuthorize("hasAuthority('REFUND_APPROVE')")
	public RefundRequestService.RecoveryResult verifyRecovery(@PathVariable String id) {
		return refundRequestService.verifyRecovery(id, security.orgId(), security.userId());
	}
}

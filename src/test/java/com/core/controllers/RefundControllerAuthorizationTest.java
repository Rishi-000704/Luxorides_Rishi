package com.core.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/*
 * Regression guard for the ops recovery workflow (Phase 4/5 of the refund
 * recovery audit): every refund endpoint must require BOTH the EMPLOYEE role
 * (class-level) and a specific Authority. In particular, verify-recovery
 * must require REFUND_APPROVE, not the weaker REFUND_VIEW -- it can change
 * financial state (COMPLETED_NEEDS_VERIFICATION -> COMPLETED or back to
 * PENDING_REVIEW), so it must never be reachable by a view-only role.
 */
class RefundControllerAuthorizationTest {

	@Test
	void controller_requiresEmployeeRole() {
		PreAuthorize classLevel = RefundController.class.getAnnotation(PreAuthorize.class);
		assertNotNull(classLevel);
		assertEquals("hasRole('EMPLOYEE')", classLevel.value());
	}

	@Test
	void getPage_requiresRefundView() {
		assertEquals("hasAuthority('REFUND_VIEW')", requiredAuthority(findMethod("getPage")));
	}

	@Test
	void approve_requiresRefundApprove() {
		assertEquals("hasAuthority('REFUND_APPROVE')", requiredAuthority(findMethod("approve")));
	}

	@Test
	void verifyRecovery_requiresRefundApprove_notJustView() {
		assertEquals("hasAuthority('REFUND_APPROVE')", requiredAuthority(findMethod("verifyRecovery")));
	}

	private Method findMethod(String name) {
		for (Method method : RefundController.class.getMethods()) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		throw new IllegalStateException("Method not found: " + name);
	}

	private String requiredAuthority(Method method) {
		PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
		assertNotNull(annotation, "Missing @PreAuthorize on " + method.getName());
		return annotation.value();
	}
}

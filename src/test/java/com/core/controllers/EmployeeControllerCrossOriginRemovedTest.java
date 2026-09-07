package com.core.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.CrossOrigin;

/*
 * Regression guard: these three employee-only, authority-gated controllers
 * previously carried a redundant class-level @CrossOrigin("*") on top of
 * the centralized (and, until this change, also wildcard) CORS policy.
 * They should now inherit the centralized, environment-driven policy from
 * SecurityConfiguration instead of independently declaring their own
 * wildcard -- if @CrossOrigin("*") is ever re-added to one of these, this
 * test catches it rather than letting it silently reopen the gap.
 */
class EmployeeControllerCrossOriginRemovedTest {

	@Test
	void employeeDriverDutyLinkController_hasNoClassLevelCrossOrigin() {
		assertNull(EmployeeDriverDutyLinkController.class.getAnnotation(CrossOrigin.class));
	}

	@Test
	void employeeEstimateController_hasNoClassLevelCrossOrigin() {
		assertNull(EmployeeEstimateController.class.getAnnotation(CrossOrigin.class));
	}

	@Test
	void employeeDriverDutySubmissionController_hasNoClassLevelCrossOrigin() {
		assertNull(EmployeeDriverDutySubmissionController.class.getAnnotation(CrossOrigin.class));
	}

	/*
	 * The other side of this change: these two ARE intentionally public,
	 * token-based endpoints and were deliberately left untouched per
	 * instruction. Locks that decision in so it isn't accidentally narrowed
	 * or widened later without a conscious change to this test.
	 */
	@Test
	void externalDriverDutyController_stillHasWildcardCrossOrigin() {
		CrossOrigin annotation = ExternalDriverDutyController.class.getAnnotation(CrossOrigin.class);
		assertNotNull(annotation);
		assertEquals("*", annotation.value()[0]);
	}

	@Test
	void publicEstimateController_stillHasWildcardCrossOrigin() {
		CrossOrigin annotation = PublicEstimateController.class.getAnnotation(CrossOrigin.class);
		assertNotNull(annotation);
		assertEquals("*", annotation.value()[0]);
	}
}

package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.core.dtos.pub.AppVersionCheckResponse;

/*
 * Force-update gate correctness: the backend is the sole authority on
 * whether an installed version must update, and must never brick a
 * driver's app due to a malformed/missing version report (fail-safe).
 */
class AppVersionServiceTest {

	private final AppVersionService service = new AppVersionService("1.2.0", "1.5.0");

	@Test
	void anInstalledVersionBelowMinimumMustForceUpdate() {
		AppVersionCheckResponse result = service.check("1.1.9");

		assertTrue(result.forceUpdate());
		assertEquals("1.2.0", result.minimumSupportedVersion());
		assertEquals("1.5.0", result.latestVersion());
	}

	@Test
	void anInstalledVersionExactlyAtMinimumDoesNotForceUpdate() {
		assertFalse(service.check("1.2.0").forceUpdate());
	}

	@Test
	void anInstalledVersionAboveMinimumDoesNotForceUpdate() {
		assertFalse(service.check("1.5.0").forceUpdate());
	}

	@Test
	void anInstalledVersionAboveLatestStillDoesNotForceUpdate_backendNeverPunishesAHigherClientVersion() {
		assertFalse(service.check("2.0.0").forceUpdate());
	}

	@Test
	void aMissingInstalledVersionFailsSafeAndDoesNotForceUpdate() {
		assertFalse(service.check(null).forceUpdate());
	}

	@Test
	void aBlankInstalledVersionFailsSafeAndDoesNotForceUpdate() {
		assertFalse(service.check("  ").forceUpdate());
	}

	@Test
	void aGarbageInstalledVersionFailsSafeAndDoesNotForceUpdate() {
		assertFalse(service.check("not-a-version").forceUpdate());
	}

	@Test
	void aShorterVersionStringComparesMissingSegmentsAsZero() {
		AppVersionService s = new AppVersionService("1.2.0", "1.2.0");

		// "1.2" == "1.2.0" -- not below minimum.
		assertFalse(s.check("1.2").forceUpdate());
		// "1.1" < "1.2.0" -- below minimum.
		assertTrue(s.check("1.1").forceUpdate());
	}

	@Test
	void compareRanksNumericallyNotLexically() {
		// A lexical string compare would rank "1.9.0" above "1.10.0" --
		// this must not happen.
		assertTrue(AppVersionService.compare("1.10.0", "1.9.0") > 0);
		assertTrue(AppVersionService.compare("1.9.0", "1.10.0") < 0);
	}

	@Test
	void compareTreatsEqualVersionsAsEqual() {
		assertEquals(0, AppVersionService.compare("2.3.4", "2.3.4"));
	}
}

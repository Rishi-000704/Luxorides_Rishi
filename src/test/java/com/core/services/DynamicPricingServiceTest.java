package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.core.dtos.pricing.DynamicPricingResponse;
import com.core.models.Org;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverRepository;
import com.core.services.config.OrgService;

/*
 * Covers the P1.1 COUNT() fix: computeMultiplier used to call
 * driverRepository.findByOrgId(orgId).size() (loading every Driver row/column
 * only to discard everything but a count). It now calls
 * driverRepository.countByOrgId(orgId) instead -- these tests prove the
 * replacement is semantically identical (same totalDrivers/idleDrivers feed
 * into the exact same multiplier formula, unchanged) and that the old
 * full-entity-load path is no longer exercised at all.
 */
class DynamicPricingServiceTest {

	private final BookingEntryRepository bookingEntryRepository = mock(BookingEntryRepository.class);
	private final DriverRepository driverRepository = mock(DriverRepository.class);
	private final OrgService orgService = mock(OrgService.class);

	private final DynamicPricingService service =
			new DynamicPricingService(bookingEntryRepository, driverRepository, orgService);

	private Org orgWithDynamicPricing(boolean enabled, BigDecimal maxMultiplier) {
		Org org = new Org();
		org.setDynamicPricingEnabled(enabled);
		org.setDynamicPricingMaxMultiplier(maxMultiplier);
		return org;
	}

	@Test
	void computeMultiplier_usesCountByOrgId_notFindByOrgIdSize() {
		when(orgService.getOrg("org-1")).thenReturn(orgWithDynamicPricing(true, BigDecimal.valueOf(1.5)));
		when(bookingEntryRepository.countByOrgIdAndStatusIn(eq("org-1"), any())).thenReturn(4L);
		when(driverRepository.countByOrgId("org-1")).thenReturn(10L);
		when(bookingEntryRepository.countDistinctBusyDrivers(eq("org-1"), any())).thenReturn(6L);

		service.computeMultiplier("org-1");

		verify(driverRepository).countByOrgId("org-1");
		verify(driverRepository, never()).findByOrgId(anyString());
	}

	@Test
	void computeMultiplier_idleDriverCount_matchesTotalMinusBusy() {
		when(orgService.getOrg("org-1")).thenReturn(orgWithDynamicPricing(true, BigDecimal.valueOf(1.5)));
		when(bookingEntryRepository.countByOrgIdAndStatusIn(eq("org-1"), any())).thenReturn(4L);
		when(driverRepository.countByOrgId("org-1")).thenReturn(10L);
		when(bookingEntryRepository.countDistinctBusyDrivers(eq("org-1"), any())).thenReturn(6L);

		DynamicPricingResponse response = service.computeMultiplier("org-1");

		// 10 total (via countByOrgId) - 6 busy = 4 idle -- identical arithmetic
		// to the pre-fix findByOrgId(orgId).size() - busyDrivers.
		assertEquals(4L, response.idleDrivers());
		assertEquals(4L, response.pendingDuties());
	}

	@Test
	void computeMultiplier_idleDriversNeverNegative_whenBusyExceedsTotal() {
		when(orgService.getOrg("org-1")).thenReturn(orgWithDynamicPricing(true, BigDecimal.valueOf(1.5)));
		when(bookingEntryRepository.countByOrgIdAndStatusIn(eq("org-1"), any())).thenReturn(2L);
		when(driverRepository.countByOrgId("org-1")).thenReturn(3L);
		when(bookingEntryRepository.countDistinctBusyDrivers(eq("org-1"), any())).thenReturn(5L);

		DynamicPricingResponse response = service.computeMultiplier("org-1");

		assertEquals(0L, response.idleDrivers());
	}

	@Test
	void computeMultiplier_disabledOrg_stillReportsRealCounts() {
		when(orgService.getOrg("org-1")).thenReturn(orgWithDynamicPricing(false, BigDecimal.valueOf(1.5)));
		when(bookingEntryRepository.countByOrgIdAndStatusIn(eq("org-1"), any())).thenReturn(7L);
		when(driverRepository.countByOrgId("org-1")).thenReturn(20L);
		when(bookingEntryRepository.countDistinctBusyDrivers(eq("org-1"), any())).thenReturn(5L);

		DynamicPricingResponse response = service.computeMultiplier("org-1");

		assertEquals(false, response.enabled());
		assertEquals(15L, response.idleDrivers());
		assertEquals(BigDecimal.ONE, response.multiplier());
		verify(driverRepository, never()).findByOrgId(anyString());
	}
}

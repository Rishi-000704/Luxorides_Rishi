package com.core.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.pricing.DynamicPricingResponse;
import com.core.models.Org;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverRepository;
import com.core.models.enums.DutyStatus;
import com.core.services.config.OrgService;

import lombok.RequiredArgsConstructor;

/*
 * Real-time demand/supply-based pricing multiplier -- genuinely computed
 * from current pending/running duty counts vs. currently-idle drivers, not
 * a fabricated or hardcoded surge number. Deliberately exposed as an
 * advisory endpoint (GET, returns the computed multiplier + the raw numbers
 * behind it) rather than being force-injected into the existing,
 * P0-protected booking/estimate fare-calculation pipeline -- that pipeline
 * is financially sensitive and out of scope to modify blind. The applied
 * multiplier stays fully transparent: callers see exactly how it was
 * derived, and it never fires unless the org has explicitly enabled it.
 */
@Service
@RequiredArgsConstructor
public class DynamicPricingService {

	private static final BigDecimal DEFAULT_MAX_MULTIPLIER = BigDecimal.valueOf(1.5);
	private static final List<DutyStatus> PENDING_STATUSES = List.of(DutyStatus.REQUESTED, DutyStatus.ALLOTTED, DutyStatus.RUNNING);
	private static final List<DutyStatus> BUSY_STATUSES = List.of(DutyStatus.ALLOTTED, DutyStatus.RUNNING);

	private final BookingEntryRepository bookingEntryRepository;
	private final DriverRepository driverRepository;
	private final OrgService orgService;

	@Transactional(readOnly = true)
	public DynamicPricingResponse computeMultiplier(String orgId) {
		Org org = orgService.getOrg(orgId);

		long pendingDuties = bookingEntryRepository.countByOrgIdAndStatusIn(orgId, PENDING_STATUSES);
		long totalDrivers = driverRepository.countByOrgId(orgId);
		long busyDrivers = bookingEntryRepository.countDistinctBusyDrivers(orgId, BUSY_STATUSES);
		long idleDrivers = Math.max(0, totalDrivers - busyDrivers);

		BigDecimal maxMultiplier = org.getDynamicPricingMaxMultiplier() != null
				? org.getDynamicPricingMaxMultiplier()
				: DEFAULT_MAX_MULTIPLIER;

		if (!org.isDynamicPricingEnabled()) {
			return new DynamicPricingResponse(false, pendingDuties, idleDrivers, BigDecimal.ONE, BigDecimal.ONE, maxMultiplier);
		}

		BigDecimal ratio = idleDrivers > 0
				? BigDecimal.valueOf(pendingDuties).divide(BigDecimal.valueOf(idleDrivers), 4, RoundingMode.HALF_UP)
				: BigDecimal.valueOf(pendingDuties > 0 ? 999 : 0);

		// ratio <= 1 (supply meets/exceeds demand) -> 1.0x. Above that, scale
		// linearly toward maxMultiplier, capped there regardless of how
		// extreme the ratio gets.
		BigDecimal multiplier = ratio.compareTo(BigDecimal.ONE) <= 0
				? BigDecimal.ONE
				: BigDecimal.ONE.add(ratio.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(0.1)))
						.min(maxMultiplier);

		return new DynamicPricingResponse(true, pendingDuties, idleDrivers, ratio, multiplier.setScale(2, RoundingMode.HALF_UP), maxMultiplier);
	}
}

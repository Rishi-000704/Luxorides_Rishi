package com.core.dtos.purchase;

import java.util.List;

/*
 * Phase A: bulk counterpart to the existing single-duty
 * GET /purchase-invoice/vendors/{vendorId}/duties/{bookingEntryId}/packages.
 * One entry per requested bookingEntryId, in request order, so the caller
 * never needs to re-derive which result belongs to which duty. A per-duty
 * failure (already invoiced, wrong vendor, missing package snapshot, etc.)
 * is reported here instead of failing the whole batch -- matching the
 * existing frontend behavior where each duty's package load is independent.
 */
public record PurchasePackageOptionsForDutyDTO(
        String bookingEntryId,
        List<PurchasePackageOptionDTO> packages,
        String error
) {
}

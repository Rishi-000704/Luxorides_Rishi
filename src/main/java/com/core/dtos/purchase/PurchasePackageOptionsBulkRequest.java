package com.core.dtos.purchase;

import java.util.List;

public record PurchasePackageOptionsBulkRequest(
        List<String> bookingEntryIds
) {
}

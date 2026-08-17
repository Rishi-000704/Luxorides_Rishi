package com.core.dtos.config;

import java.time.Instant;

public record FinancialYearRequest(String id, String orgBillingEntityId, Instant startDate, Instant endDate,
		String invoicePrefix, Integer invoiceCounter) {

}

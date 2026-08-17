package com.core.dtos.invoice;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoicePendingListItem(
		String invoiceNumber,
	    Instant invoiceDate,
	    String clientName,
	    String clientPhoneNumber,
	    String corporateName,
	    String corporatePhoneNumber,
	    BigDecimal pendingAmount
		) {

}

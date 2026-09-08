package com.core.dtos.driverduty;

import java.math.BigDecimal;
import java.time.Instant;

/*
 * Mirrors CloseDutyConfirmationResponse/GarageReturnConfirmationResponse's
 * shape -- amount/paymentId/confirmedAt always reflect the authoritative,
 * backend-computed row (never the amount the client submitted, since none is
 * ever accepted). confirmed is always true on a 2xx response; failure comes
 * back as a thrown BusinessException instead, same convention as every other
 * driver-duty confirmation endpoint.
 */
public record CashPaymentConfirmationResponse(
		boolean confirmed,
		String paymentId,
		BigDecimal amount,
		Instant confirmedAt,
		String message
) {
}

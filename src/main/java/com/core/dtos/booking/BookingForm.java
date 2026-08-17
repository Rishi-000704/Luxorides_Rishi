package com.core.dtos.booking;

import java.math.BigDecimal;

import com.core.models.enums.GstType;

public record BookingForm(
		String bookingId,
		String clientId,
		String clientBillingEntityId,
		String remarks,
		GstType gstType,
		Integer gstRate,
		BigDecimal discountAmount
) {
}
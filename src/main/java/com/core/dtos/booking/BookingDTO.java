package com.core.dtos.booking;

import java.time.Instant;
import java.util.List;

import com.core.dtos.client.ClientBillingEntityDTO;
import com.core.dtos.client.ClientDTO;
import com.core.dtos.common.MoneyDTO;
import com.core.dtos.payment.PaymentDTO;
import com.core.models.embedded.GstSnapshot;
import com.core.models.enums.BookingStatus;

public record BookingDTO(

		String bookingId,
		String orgId,
		BookingStatus status,
		GstSnapshot gstSnapshot,
		String remarks,
		String invoiceNumber,

		ClientDTO client,
		ClientBillingEntityDTO billingEntity,

		List<BookingEntryDTO> entries,
		List<PaymentDTO> payments,

		MoneyDTO discount,
		MoneyDTO total,

		Instant createdAt,
		Instant updatedAt,
		String createdBy,
		String updatedBy
) {
}
package com.core.dtos.estimate;

import java.time.Instant;
import java.util.List;

import com.core.dtos.client.ClientBillingEntityDTO;
import com.core.dtos.client.ClientDTO;
import com.core.dtos.common.MoneyDTO;
import com.core.dtos.payment.PaymentDTO;
import com.core.models.embedded.GstSnapshot;
import com.core.models.enums.EstimateStatus;

public record EstimateDTO(
		String id,
		String estimateId,
		String orgId,
		EstimateStatus status,
		Instant estimateDate,
		Instant validTill,
		ClientDTO client,
		ClientBillingEntityDTO billingEntity,
		MoneyDTO subtotal,
		MoneyDTO discount,
		MoneyDTO taxableAmount,
		GstSnapshot gstSnapshot,
		MoneyDTO estimatedPayable,
		MoneyDTO advanceAmount,
		String remarks,
		List<EstimateEntryDTO> entries,
		List<PaymentDTO> payments,
		Instant sentAt,
		Instant viewedAt,
		Instant paymentInitiatedAt,
		Instant paidAt,
		Instant convertedAt,
		String convertedBookingId,
		Instant createdAt,
		Instant updatedAt,
		String createdBy,
		String updatedBy) {
}
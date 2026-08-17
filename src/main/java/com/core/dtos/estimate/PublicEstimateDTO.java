package com.core.dtos.estimate;

import java.time.Instant;
import java.util.List;

import com.core.dtos.common.MoneyDTO;
import com.core.models.embedded.GstSnapshot;
import com.core.models.enums.EstimateStatus;

public record PublicEstimateDTO(

		String estimateId,

		EstimateStatus status,

		String clientName,

		String clientPhone,

		String clientEmail,

		Instant estimateDate,

		Instant validTill,

		MoneyDTO subtotal,

		MoneyDTO discount,

		MoneyDTO taxableAmount,

		GstSnapshot gstSnapshot,

		MoneyDTO estimatedPayable,

		MoneyDTO advanceAmount,

		MoneyDTO payableNow,

		String remarks,

		Instant createdAt,

		String estimateOwner,

		List<PublicEstimateEntryDTO> entries) {
}
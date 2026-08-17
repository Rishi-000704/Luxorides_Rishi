package com.core.dtos.estimate;

import java.time.Instant;

import com.core.dtos.common.MoneyDTO;
import com.core.models.Estimate;
import com.core.models.embedded.Money;
import com.core.models.enums.EstimateStatus;

public record EstimateListItem(
		String estimateId,
		EstimateStatus status,
		String clientName,
		MoneyDTO estimatedPayable,
		MoneyDTO advanceAmount,
		Instant estimateDate,
		Instant validTill,
		Instant viewedAt,
		Instant paidAt,
		String convertedBookingId,
		Instant createdAt) {

	public static EstimateListItem from(Estimate e) {
		String clientName = null;

		if (e.getClient() != null && e.getClient().getName() != null) {
			clientName = e.getClient().getName().getDisplayName();
		}

		return new EstimateListItem(
				e.getEstimateId(),
				e.getStatus(),
				clientName,
				toMoneyDTO(e.getEstimatedPayable()),
				toMoneyDTO(e.getAdvanceAmount()),
				e.getEstimateDate(),
				e.getValidTill(),
				e.getViewedAt(),
				e.getPaidAt(),
				e.getConvertedBookingId(),
				e.getCreatedAt());
	}

	private static MoneyDTO toMoneyDTO(Money m) {
		return m == null ? null : new MoneyDTO(m.getAmount(), m.getCurrency());
	}
}
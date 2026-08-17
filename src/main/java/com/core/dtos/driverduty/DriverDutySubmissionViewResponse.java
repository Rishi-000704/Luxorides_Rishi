package com.core.dtos.driverduty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.core.models.enums.DriverDutyCheckpointStatus;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DriverDutyExpenseStatus;
import com.core.models.enums.DriverDutyExpenseType;
import com.core.models.enums.DriverDutyTokenStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;

public record DriverDutySubmissionViewResponse(
		String bookingId,
		String dutyId,
		DutyStatus dutyStatus,

		LinkStatus link,

		CheckpointSubmission startSubmission,
		CheckpointSubmission endSubmission,

		List<ExpenseSubmission> expenses,

		CompletionSummary summary,

		PaymentCollection payment
) {

	public record LinkStatus(
			boolean generated,
			DriverDutyTokenStatus status,
			Instant expiresAt,
			Instant revokedAt,
			Instant lastUsedAt,
			Instant createdAt
	) {}

	public record CheckpointSubmission(
			String checkpointId,
			DriverDutyCheckpointType type,
			DriverDutyCheckpointStatus status,

			Integer odometerKm,
			String odometerPhoto,
			String odometerPhotoUrl,

			AddressView location,
			Double accuracyMeters,
			Instant locationCapturedAt,

			Instant submittedAt,
			String notes,

			String ipAddress,
			String userAgent
	) {}

	public record AddressView(
			String formattedAddress,
			String googlePlaceId,
			Double latitude,
			Double longitude,
			boolean geoVerified,
			boolean valid
	) {}

	public record ExpenseSubmission(
			String expenseId,
			DriverDutyExpenseType type,
			BigDecimal amount,
			String currency,
			String description,
			String receiptPhoto,
			String receiptPhotoUrl,
			DriverDutyExpenseStatus status
	) {}

	public record CompletionSummary(
			Integer startKm,
			Integer driverSubmittedEndKm,
			Integer billableEndKm,
			Integer totalBillableKm,

			Instant startAt,
			Instant actualDropAt,
			Instant billableEndAt,

			boolean garageReturnEstimated,

			BigDecimal extraChargesTotal,
			BigDecimal bookingTotal,
			BigDecimal amountToCollect
	) {}

	public record PaymentCollection(
			boolean available,
			PaymentStatus status,
			PaymentGateway gateway,

			BigDecimal amount,
			String currency,

			String gatewayQrCodeId,
			String gatewayPaymentId,
			String gatewayQrImageUrl,

			Instant transactionDate,
			Instant expiresAt
	) {}
}
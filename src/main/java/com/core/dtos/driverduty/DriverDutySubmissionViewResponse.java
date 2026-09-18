package com.core.dtos.driverduty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.core.models.enums.CleanlinessRating;
import com.core.models.enums.DriverDutyCheckpointStatus;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DriverDutyExpenseStatus;
import com.core.models.enums.DriverDutyExpenseType;
import com.core.models.enums.DriverDutyTokenStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.FuelLevel;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.models.enums.VehicleConditionRating;

public record DriverDutySubmissionViewResponse(
		String bookingId,
		String dutyId,
		DutyStatus dutyStatus,

		LinkStatus link,

		CheckpointSubmission startSubmission,
		CheckpointSubmission endSubmission,

		List<ExpenseSubmission> expenses,

		CompletionSummary summary,

		PaymentCollection payment,

		InspectionSubmission inspection
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

	// Uniform selfie + vehicle exterior/interior photos from Duty Readiness
	// (VehicleInspection, submitted before startDuty mints an execution
	// token -- see VehicleInspectionService). Surfaced here so an employee
	// can manually compare the uniform selfie against the driver's on-file
	// KYC photo for this specific duty, not just review it once at
	// onboarding -- null when no inspection has been submitted for this duty.
	public record InspectionSubmission(
			String uniformSelfiePhotoUrl,

			String exteriorFrontPhotoUrl,
			String exteriorBackPhotoUrl,
			String exteriorLeftPhotoUrl,
			String exteriorRightPhotoUrl,

			String interiorDashboardPhotoUrl,
			String interiorFrontSeatsPhotoUrl,
			String interiorBackSeatsPhotoUrl,
			String interiorBootSpacePhotoUrl,

			VehicleConditionRating exteriorCondition,
			VehicleConditionRating interiorCondition,
			String damageNotes,
			CleanlinessRating cleanliness,
			VehicleConditionRating tyreCondition,
			VehicleConditionRating lightsCondition,
			FuelLevel fuelLevel,

			boolean driverConfirmed,
			Instant submittedAt
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
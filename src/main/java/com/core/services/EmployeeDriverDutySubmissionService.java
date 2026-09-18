package com.core.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.driverduty.DriverDutySubmissionViewResponse;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutyCheckpoint;
import com.core.models.DriverDutyExpense;
import com.core.models.Payment;
import com.core.models.VehicleInspection;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.FileAccessCategory;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyAccessTokenRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverDutyExpenseRepository;
import com.core.repositories.PaymentRepository;
import com.core.repositories.VehicleInspectionRepository;
import com.core.services.common.FileAccessTokenService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeDriverDutySubmissionService {

	private static final String DRIVER_DUTY_QR_CONTEXT = "DRIVER_DUTY_QR";

	private final BookingEntryRepository bookingEntryRepository;
	private final DriverDutyCheckpointRepository checkpointRepository;
	private final DriverDutyExpenseRepository expenseRepository;
	private final DriverDutyAccessTokenRepository tokenRepository;
	private final PaymentRepository paymentRepository;
	private final VehicleInspectionRepository vehicleInspectionRepository;
	private final FileAccessTokenService fileAccessTokenService;

	@Transactional(readOnly = true)
	public DriverDutySubmissionViewResponse getDutySubmissionView(
			String bookingId,
			String dutyId,
			String orgId
	) {
		BookingEntry entry = bookingEntryRepository
				.findForDriverDutySubmissionView(orgId, bookingId, dutyId)
				.orElseThrow(() -> new NotFoundException(
						ErrorCode.DUTY_NOT_FOUND,
						"Duty not found"
				));

		DriverDutyCheckpoint startCheckpoint = checkpointRepository
				.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(
						entry.getId(),
						DriverDutyCheckpointType.START
				)
				.orElse(null);

		DriverDutyCheckpoint endCheckpoint = checkpointRepository
				.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(
						entry.getId(),
						DriverDutyCheckpointType.END
				)
				.orElse(null);

		List<DriverDutyExpense> expenses = expenseRepository
				.findByBookingEntry_IdOrderByCreatedAtAsc(entry.getId());

		DriverDutyAccessToken latestToken = tokenRepository
				.findFirstByBookingEntry_IdOrderByCreatedAtDesc(entry.getId())
				.orElse(null);

		Payment payment = paymentRepository
				.findFirstByOrgIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						orgId,
						DRIVER_DUTY_QR_CONTEXT,
						dutyId
				)
				.orElse(null);

		BigDecimal extraChargesTotal = expenses.stream()
				.map(this::expenseAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal bookingTotal = amount(entry.getBooking().getTotal());
		BigDecimal amountToCollect = calculatePendingAmount(entry.getBooking());

		VehicleInspection inspection = vehicleInspectionRepository
				.findByDutyIdAndOrgId(dutyId, orgId)
				.orElse(null);

		return new DriverDutySubmissionViewResponse(
				entry.getBooking().getBookingId(),
				entry.getDutyId(),
				entry.getStatus(),

				toLinkStatus(latestToken),

				toCheckpointView(startCheckpoint, orgId),
				toCheckpointView(endCheckpoint, orgId),

				expenses.stream()
						.map(expense -> toExpenseView(expense, orgId))
						.toList(),

				toSummaryView(
						entry,
						endCheckpoint,
						extraChargesTotal,
						bookingTotal,
						amountToCollect
				),

				toPaymentView(payment),

				toInspectionView(inspection, orgId)
		);
	}

	private DriverDutySubmissionViewResponse.InspectionSubmission toInspectionView(
			VehicleInspection inspection,
			String orgId
	) {
		if (inspection == null) {
			return null;
		}

		return new DriverDutySubmissionViewResponse.InspectionSubmission(
				fileUrl(inspection.getUniformSelfiePhoto(), orgId),

				fileUrl(inspection.getExteriorFrontPhoto(), orgId),
				fileUrl(inspection.getExteriorBackPhoto(), orgId),
				fileUrl(inspection.getExteriorLeftPhoto(), orgId),
				fileUrl(inspection.getExteriorRightPhoto(), orgId),

				fileUrl(inspection.getInteriorDashboardPhoto(), orgId),
				fileUrl(inspection.getInteriorFrontSeatsPhoto(), orgId),
				fileUrl(inspection.getInteriorBackSeatsPhoto(), orgId),
				fileUrl(inspection.getInteriorBootSpacePhoto(), orgId),

				inspection.getExteriorCondition(),
				inspection.getInteriorCondition(),
				inspection.getDamageNotes(),
				inspection.getCleanliness(),
				inspection.getTyreCondition(),
				inspection.getLightsCondition(),
				inspection.getFuelLevel(),

				inspection.isDriverConfirmed(),
				inspection.getSubmittedAt()
		);
	}

	private DriverDutySubmissionViewResponse.LinkStatus toLinkStatus(
			DriverDutyAccessToken token
	) {
		if (token == null) {
			return new DriverDutySubmissionViewResponse.LinkStatus(
					false,
					null,
					null,
					null,
					null,
					null
			);
		}

		return new DriverDutySubmissionViewResponse.LinkStatus(
				true,
				token.getStatus(),
				token.getExpiresAt(),
				token.getRevokedAt(),
				token.getLastUsedAt(),
				token.getCreatedAt()
		);
	}

	private DriverDutySubmissionViewResponse.CheckpointSubmission toCheckpointView(
			DriverDutyCheckpoint checkpoint,
			String orgId
	) {
		if (checkpoint == null) {
			return null;
		}

		return new DriverDutySubmissionViewResponse.CheckpointSubmission(
				checkpoint.getId(),
				checkpoint.getCheckpointType(),
				checkpoint.getStatus(),

				checkpoint.getOdometerKm(),
				checkpoint.getOdometerPhoto(),
				fileUrl(checkpoint.getOdometerPhoto(), orgId),

				toAddressView(checkpoint.getLocation()),
				checkpoint.getAccuracyMeters(),
				checkpoint.getLocationCapturedAt(),

				checkpoint.getSubmittedAt(),
				checkpoint.getNotes(),

				checkpoint.getIpAddress(),
				checkpoint.getUserAgent()
		);
	}

	private DriverDutySubmissionViewResponse.AddressView toAddressView(
			AddressSnapshot address
	) {
		if (address == null) {
			return null;
		}

		return new DriverDutySubmissionViewResponse.AddressView(
				address.getFormattedAddress(),
				address.getGooglePlaceId(),
				address.getLatitude(),
				address.getLongitude(),
				address.isGeoVerified(),
				address.isvalid()
		);
	}

	private DriverDutySubmissionViewResponse.ExpenseSubmission toExpenseView(
			DriverDutyExpense expense,
			String orgId
	) {
		return new DriverDutySubmissionViewResponse.ExpenseSubmission(
				expense.getId(),
				expense.getExpenseType(),
				amount(expense.getAmount()),
				currency(expense.getAmount()),
				expense.getDescription(),
				expense.getReceiptPhoto(),
				fileUrl(expense.getReceiptPhoto(), orgId),
				expense.getStatus()
		);
	}

	private DriverDutySubmissionViewResponse.CompletionSummary toSummaryView(
			BookingEntry entry,
			DriverDutyCheckpoint endCheckpoint,
			BigDecimal extraChargesTotal,
			BigDecimal bookingTotal,
			BigDecimal amountToCollect
	) {
		Integer startKm = entry.getStartingKM();
		Integer driverSubmittedEndKm = endCheckpoint != null
				? endCheckpoint.getOdometerKm()
				: null;

		Integer billableEndKm = entry.getClosingKM();

		Integer totalBillableKm = null;
		if (startKm != null && billableEndKm != null) {
			totalBillableKm = billableEndKm - startKm;
		}

		boolean garageReturnEstimated = isGarageReturnEstimated(
				entry,
				driverSubmittedEndKm
		);

		return new DriverDutySubmissionViewResponse.CompletionSummary(
				startKm,
				driverSubmittedEndKm,
				billableEndKm,
				totalBillableKm,

				entry.getStartAt(),
				entry.getDropTime(),
				entry.getEndAt(),

				garageReturnEstimated,

				extraChargesTotal,
				bookingTotal,
				amountToCollect
		);
	}

	private DriverDutySubmissionViewResponse.PaymentCollection toPaymentView(
			Payment payment
	) {
		if (payment == null) {
			return new DriverDutySubmissionViewResponse.PaymentCollection(
					false,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null
			);
		}

		return new DriverDutySubmissionViewResponse.PaymentCollection(
				true,
				payment.getStatus(),
				payment.getGateway(),

				amount(payment.getReceivedAmount()),
				currency(payment.getReceivedAmount()),

				payment.getGatewayQrCodeId(),
				payment.getGatewayPaymentId(),
				payment.getGatewayQrImageContent(),

				payment.getTransactionDate(),
				payment.getExpiresAt()
		);
	}

	private boolean isGarageReturnEstimated(
			BookingEntry entry,
			Integer driverSubmittedEndKm
	) {
		boolean kmAdjusted = driverSubmittedEndKm != null
				&& entry.getClosingKM() != null
				&& entry.getClosingKM() > driverSubmittedEndKm;

		boolean timeAdjusted = entry.getDropTime() != null
				&& entry.getEndAt() != null
				&& entry.getEndAt().isAfter(entry.getDropTime());

		return kmAdjusted || timeAdjusted;
	}

	private BigDecimal calculatePendingAmount(Booking booking) {
		BigDecimal bookingTotal = amount(booking.getTotal());

		BigDecimal paid = booking.getPayments() == null
				? BigDecimal.ZERO
				: booking.getPayments()
						.stream()
						.filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
						.map(p -> amount(p.getReceivedAmount()))
						.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal pending = bookingTotal.subtract(paid);

		return pending.compareTo(BigDecimal.ZERO) < 0
				? BigDecimal.ZERO
				: pending;
	}

	private BigDecimal expenseAmount(DriverDutyExpense expense) {
		return amount(expense.getAmount());
	}

	private BigDecimal amount(com.core.models.embedded.Money money) {
		if (money == null || money.getAmount() == null) {
			return BigDecimal.ZERO;
		}

		return money.getAmount();
	}

	private String currency(com.core.models.embedded.Money money) {
		if (money == null || money.getCurrency() == null) {
			return null;
		}

		return money.getCurrency().name();
	}

	private String fileUrl(String fileName, String orgId) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}

		return "/file/" + fileAccessTokenService.toAccessUrl(fileName, orgId, FileAccessCategory.PRIVATE);
	}
}
package com.core.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.booking.BookingDTO;
import com.core.dtos.booking.BookingForm;
import com.core.dtos.booking.DutyForm;
import com.core.dtos.client.app.CancellationPreviewResponse;
import com.core.dtos.client.app.ClientBookingDraftDTO;
import com.core.dtos.client.app.TripRatingRequest;
import com.core.dtos.client.app.TripRatingResponse;
import com.core.dtos.driverduty.DriverDutyLocationResponse;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Org;
import com.core.models.Payment;
import com.core.models.TripRating;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.Currency;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.GstType;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyLiveLocationRepository;
import com.core.repositories.TripRatingRepository;
import com.core.services.config.OrgService;
import com.core.util.EtaEstimator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientBookingService {

	private final BookingService bookingService;
	private final BookingEntryRepository bookingEntryRepository;
	private final DriverDutyLiveLocationRepository liveLocationRepository;
	private final TripRatingRepository tripRatingRepository;
	private final CancellationPolicyService cancellationPolicyService;
	private final OrgService orgService;

	@PersistenceContext
	private EntityManager entityManager;

	/* ================= DRAFT BOOKING ================= */

	@Transactional
	public Booking draftBooking(ClientBookingDraftDTO form, String loggedInClientId, String orgId) {

		BookingForm bform = new BookingForm(null, loggedInClientId, form.clientBillingEntityId(), "", GstType.IGST,
				form.clientBillingEntityId() != null ? 18 : 5, BigDecimal.ZERO);
		BookingDTO booking = bookingService.addBooking(bform, orgId);

		for (ClientBookingDraftDTO.Entry entry : form.entries()) {
			DutyForm dform = new DutyForm(booking.bookingId(), null, entry.passengerIds(), entry.reportingTime(),
					entry.reportingLocation(), entry.dropLocation(), entry.dropTime(), entry.vehicleId(),
					entry.flightNumber(), entry.packageId(), entry.clientNotes());
			bookingService.addBookingEntry(dform, orgId);
		}
		entityManager.flush();
		entityManager.clear();
		return this.getBooking(booking.bookingId(), orgId);
	}

	/* ================= CONFIRM (NON-GATEWAY PAYMENTS) ================= */

	@Transactional
	public Booking confirmBooking(String orgId, String bookingId) {

		Booking booking = bookingService.getBooking(bookingId, orgId);

		/* ================= STATUS ================= */

		if (booking.getStatus() != BookingStatus.DRAFT) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Booking already processed");
		}

		/* ================= FINANCIAL VALIDATION ================= */

		Money bookingTotal = booking.getTotal();
		if (bookingTotal == null || bookingTotal.getAmount() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Booking total is not calculated");
		}

		Currency currency = bookingTotal.getCurrency();

		// Sum confirmed payments
		BigDecimal paidAmount = booking.getPayments().stream().filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
				.map(Payment::getReceivedAmount).peek(m -> {
					if (!currency.equals(m.getCurrency())) {
						throw new BusinessException(ErrorCode.BAD_REQUEST, "Payment currency mismatch. Expected: " + currency + ", Found: " + m.getCurrency());
					}
				}).map(Money::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal bookingAmount = bookingTotal.getAmount();

		if (paidAmount.compareTo(bookingAmount) < 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Insufficient payment. Paid: " + paidAmount + ", Required: " + bookingAmount);
		}

		if (paidAmount.compareTo(bookingAmount) > 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Overpayment detected. Paid: " + paidAmount + ", Required: " + bookingAmount);
		}

		/* ================= CONFIRM ================= */

		bookingService.confirmBooking(bookingId, orgId);

		return this.getBooking(bookingId, orgId);
	}

	/* ================= READ APIs ================= */

	@Transactional(readOnly = true)
	public List<Booking> getMyBookings(String clientId, String orgId) {
		return bookingService.getClientBookings(clientId, orgId);
	}

	@Transactional(readOnly = true)
	public Booking getBooking(String bookingId, String orgId) {
		return bookingService.getBooking(bookingId, orgId);
	}

	/*
	 * P0 IDOR fix -- getBooking(bookingId, orgId) above is org-scoped only and
	 * must stay that way for internal/system callers (draftBooking,
	 * confirmBooking) that don't act on behalf of a specific client. Every
	 * customer-facing read/action must go through this instead, which adds
	 * the client-ownership check on top -- same shape as
	 * getOwnedCancellableBooking below, which now delegates here.
	 */
	@Transactional(readOnly = true)
	public Booking getOwnedBooking(String bookingId, String clientId, String orgId) {
		Booking booking = bookingService.getBooking(bookingId, orgId);

		if (!clientId.equals(booking.getClientId())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "This booking does not belong to you");
		}

		return booking;
	}

	/*
	 * REST fallback for the live-location WebSocket channel -- used when a
	 * client reconnects after a gap, or opens the booking detail view before
	 * any push has arrived yet.
	 *
	 * P0 IDOR fix -- previously scoped only by orgId, so any authenticated
	 * client in the same org could read another client's live vehicle GPS
	 * position by guessing/knowing a dutyId. Now requires the requesting
	 * client to own the booking this duty belongs to, same ownership check
	 * submitRating/getRating/createShareLink already apply.
	 */
	@Transactional(readOnly = true)
	public Optional<DriverDutyLocationResponse> getDutyLocation(String dutyId, String clientId, String orgId) {
		BookingEntry entry = bookingEntryRepository.findByDutyIdAndOrgId(dutyId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		if (!clientId.equals(entry.getBooking().getClientId())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "This duty does not belong to you");
		}

		return liveLocationRepository.findByDutyId(entry.getDutyId())
				.map(location -> {
					EtaEstimator.Estimate eta = EtaEstimator.estimate(
							entry.getDropLocation(),
							location.getLatitude(),
							location.getLongitude(),
							location.getSpeedMps()
					);

					return new DriverDutyLocationResponse(
							location.getDutyId(),
							location.getLatitude(),
							location.getLongitude(),
							location.getHeadingDegrees(),
							location.getCapturedAt(),
							eta.distanceRemainingKm(),
							eta.etaMinutes(),
							true
					);
				});
	}

	/*
	 * ================= TRIP RATING =================
	 * One rating per duty, allowed only once the duty is COMPLETED and only
	 * by the client who owns the booking -- both checked here, not just at
	 * the controller layer.
	 */
	@Transactional
	public TripRatingResponse submitRating(String dutyId, String clientId, String orgId, TripRatingRequest request) {
		if (request.stars() == null || request.stars() < 1 || request.stars() > 5) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Rating must be between 1 and 5 stars");
		}

		BookingEntry entry = bookingEntryRepository.findByDutyIdAndOrgId(dutyId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		if (!clientId.equals(entry.getBooking().getClientId())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "This duty does not belong to you");
		}

		if (entry.getStatus() != DutyStatus.COMPLETED) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Duty must be completed before it can be rated");
		}

		if (tripRatingRepository.existsByDutyIdAndOrgId(dutyId, orgId)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "This duty has already been rated");
		}

		TripRating rating = new TripRating();
		rating.setOrgId(orgId);
		rating.setBookingId(entry.getBooking().getBookingId());
		rating.setDutyId(dutyId);
		rating.setClientId(clientId);
		rating.setDriverId(entry.getDriverId());
		rating.setBookingEntry(entry);
		rating.setStars(request.stars());
		rating.setComment(request.comment());

		TripRating saved = tripRatingRepository.save(rating);

		return new TripRatingResponse(saved.getDutyId(), saved.getStars(), saved.getComment(), saved.getCreatedAt());
	}

	/*
	 * P1.8 -- previously scoped only by orgId, so any authenticated client in
	 * the same org could read another client's trip rating (stars + comment)
	 * by guessing/knowing a dutyId -- submitRating already checks clientId
	 * ownership (see above), this read path just hadn't. TripRating carries
	 * its own clientId, so no extra lookup is needed to enforce it here.
	 */
	@Transactional(readOnly = true)
	public Optional<TripRatingResponse> getRating(String dutyId, String clientId, String orgId) {
		return tripRatingRepository.findByDutyIdAndOrgId(dutyId, orgId)
				.filter(r -> clientId.equals(r.getClientId()))
				.map(r -> new TripRatingResponse(r.getDutyId(), r.getStars(), r.getComment(), r.getCreatedAt()));
	}

	/*
	 * ================= CANCELLATION =================
	 */

	@Transactional(readOnly = true)
	public CancellationPreviewResponse getCancellationPreview(String bookingId, String clientId, String orgId) {
		Booking booking = getOwnedCancellableBooking(bookingId, clientId, orgId);
		Org org = orgService.getOrg(orgId);

		CancellationPolicyService.Evaluation evaluation = cancellationPolicyService.evaluate(booking, org);

		return new CancellationPreviewResponse(
				evaluation.withinFreeWindow(),
				evaluation.paidAmount(),
				evaluation.feeAmount(),
				evaluation.refundAmount(),
				evaluation.freeWindowHours(),
				evaluation.feePercent()
		);
	}

	@Transactional
	public void cancelBookingWithPolicy(String bookingId, String clientId, String orgId, String reason) {
		Booking booking = getOwnedCancellableBooking(bookingId, clientId, orgId);
		Org org = orgService.getOrg(orgId);

		CancellationPolicyService.Evaluation evaluation = cancellationPolicyService.evaluate(booking, org);

		bookingService.cancelBooking(bookingId, orgId, reason, evaluation.feeAmount());
	}

	private Booking getOwnedCancellableBooking(String bookingId, String clientId, String orgId) {
		Booking booking = getOwnedBooking(bookingId, clientId, orgId);

		if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.RUNNING) {
			throw new BusinessException(
					ErrorCode.CANCELLATION_NOT_ALLOWED,
					"Booking cannot be cancelled when status is " + booking.getStatus());
		}

		return booking;
	}
}

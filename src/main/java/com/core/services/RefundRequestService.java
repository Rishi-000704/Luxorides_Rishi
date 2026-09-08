package com.core.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.payment.RefundOpsListItem;
import com.core.events.RefundCompletedEvent;
import com.core.events.assembler.RefundEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.gateway.razerpay.RefundPersistenceException;
import com.core.models.Booking;
import com.core.models.Client;
import com.core.models.RefundRequest;
import com.core.models.enums.RefundRequestStatus;
import com.core.repositories.BookingRepository;
import com.core.repositories.RefundRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundRequestService {

	private final RefundRequestRepository refundRequestRepository;
	private final BookingRepository bookingRepository;
	private final RazorpayPaymentService razorpayPaymentService;
	private final RefundEventAssembler refundEventAssembler;
	private final ApplicationEventPublisher eventPublisher;

	/*
	 * Called by RefundInitiatedListener, once per cancellation, for BOTH the
	 * employee cancellation endpoint and the client-facing one -- neither
	 * path moves real money on its own; this row is what an admin later acts
	 * on.
	 */
	@Transactional
	public void createFromCancellation(String orgId, String bookingId, BigDecimal paidAmount, BigDecimal feeAmount) {
		BigDecimal fee = feeAmount == null ? BigDecimal.ZERO : feeAmount;
		BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;

		if (paid.signum() <= 0) {
			// Nothing was ever confirmed-paid on this booking -- no refund to review.
			return;
		}

		/*
		 * Defense-in-depth idempotency guard: BookingService.cancelBooking's own
		 * CANCELLED-status guard (under a row lock) already makes it impossible
		 * for a legitimate double-cancellation to reach this method twice for
		 * the same booking, so this should never actually trigger today -- but
		 * this is the one place that inserts a RefundRequest, and this class
		 * has no reason to trust that invariant alone for something a real
		 * refund is calculated from. A booking is cancelled at most once, so at
		 * most one RefundRequest should ever exist for it, full stop.
		 */
		if (refundRequestRepository.findFirstByOrgIdAndBookingIdOrderByCreatedAtDesc(orgId, bookingId).isPresent()) {
			log.warn("Refund request already exists for booking {} -- skipping duplicate creation", bookingId);
			return;
		}

		BigDecimal refundAmount = paid.subtract(fee);
		if (refundAmount.signum() < 0) {
			refundAmount = BigDecimal.ZERO;
		}

		RefundRequest request = new RefundRequest();
		request.setOrgId(orgId);
		request.setBookingId(bookingId);
		request.setPaidAmount(paid);
		request.setFeeAmount(fee);
		request.setRefundAmount(refundAmount);
		request.setStatus(RefundRequestStatus.PENDING_REVIEW);

		refundRequestRepository.save(request);
	}

	@Transactional(readOnly = true)
	public List<RefundRequest> list(String orgId) {
		return refundRequestRepository.findByOrgIdOrderByCreatedAtDesc(orgId);
	}

	/*
	 * Ops recovery view (RefundController's paginated /page endpoint).
	 * RefundRequest.bookingId is a plain string, not a JPA relation, so
	 * enriching a page with a customer reference takes exactly one extra
	 * batch query per page -- never one per row.
	 */
	@Transactional(readOnly = true)
	public Page<RefundOpsListItem> getOpsPage(String orgId, RefundRequestStatus status, Pageable pageable) {
		Page<RefundRequest> page = status != null
				? refundRequestRepository.findByOrgIdAndStatus(orgId, status, pageable)
				: refundRequestRepository.findByOrgId(orgId, pageable);

		List<String> bookingIds = page.getContent().stream()
				.map(RefundRequest::getBookingId)
				.distinct()
				.toList();

		Map<String, Booking> bookingsById = bookingIds.isEmpty()
				? Map.of()
				: bookingRepository.findAllByBookingIdInAndOrgId(bookingIds, orgId).stream()
						.collect(Collectors.toMap(Booking::getBookingId, booking -> booking));

		return page.map(request -> toOpsListItem(request, bookingsById.get(request.getBookingId())));
	}

	private RefundOpsListItem toOpsListItem(RefundRequest request, Booking booking) {
		Client client = booking != null ? booking.getClient() : null;

		String customerName = client != null && client.getName() != null
				? client.getName().getDisplayName()
				: null;

		String customerPhone = client != null ? client.getPhone() : null;

		return new RefundOpsListItem(
				request.getId(),
				request.getBookingId(),
				customerName,
				customerPhone,
				request.getPaidAmount(),
				request.getFeeAmount(),
				request.getRefundAmount(),
				request.getStatus(),
				request.getGatewayRefundId(),
				request.getFailureReason(),
				request.getReviewedBy(),
				request.getReviewedAt(),
				request.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public List<RefundRequest> listPending(String orgId) {
		return refundRequestRepository.findByOrgIdAndStatusOrderByCreatedAtDesc(orgId, RefundRequestStatus.PENDING_REVIEW);
	}

	/*
	 * The ONE place a real Razorpay refund call ever fires -- only reachable
	 * through an employee explicitly hitting this endpoint (see
	 * RefundController), never automatically.
	 *
	 * P1.7 -- lockByIdAndOrgId (not the plain findByIdAndOrgId this used to
	 * call) serializes concurrent approve() calls for the same request id: a
	 * second call blocks here until the first one's status update has
	 * committed and released the lock, so its own PENDING_REVIEW check below
	 * always sees the first call's outcome instead of racing it. Without
	 * this, two concurrent approvals could both pass the status check and
	 * both fire a real refund.
	 */
	@Transactional
	public RefundRequest approve(String id, String orgId, String reviewedBy) {
		RefundRequest request = refundRequestRepository.lockByIdAndOrgId(id, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND, "Refund request not found"));

		if (request.getStatus() != RefundRequestStatus.PENDING_REVIEW) {
			throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_PROCESSED, "This refund request was already processed");
		}

		if (request.getRefundAmount() == null || request.getRefundAmount().signum() <= 0) {
			throw new BusinessException(ErrorCode.REFUND_FAILED, "Nothing to refund after fee deduction");
		}

		try {
			String gatewayRefundId = razorpayPaymentService.refundPayment(orgId, request.getBookingId(), request.getRefundAmount());

			request.setStatus(RefundRequestStatus.COMPLETED);
			request.setGatewayRefundId(gatewayRefundId);
			request.setReviewedBy(reviewedBy);
			request.setReviewedAt(Instant.now());

			refundRequestRepository.save(request);

			publishRefundCompleted(orgId, request);

			return request;
		} catch (RefundPersistenceException ex) {
			/*
			 * P1H -- the Razorpay refund itself succeeded (we have a real
			 * gatewayRefundId); only recording it locally failed. Never mark
			 * this plain FAILED: FAILED elsewhere on this class means "nothing
			 * happened", and this request can never pass the PENDING_REVIEW
			 * guard again to be re-approved, so preserving the refund id here is
			 * what lets anyone reviewing this row later see money already moved
			 * instead of assuming it's safe to refund through another channel.
			 */
			request.setStatus(RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION);
			request.setGatewayRefundId(ex.getRefundId());
			request.setFailureReason(ex.getMessage());
			request.setReviewedBy(reviewedBy);
			request.setReviewedAt(Instant.now());

			refundRequestRepository.save(request);

			throw new BusinessException(ErrorCode.REFUND_FAILED,
					"Refund was processed by Razorpay (refund id " + ex.getRefundId()
							+ ") but could not be fully recorded. This request now requires manual verification.");
		} catch (Exception ex) {
			request.setStatus(RefundRequestStatus.FAILED);
			request.setFailureReason(ex.getMessage());
			request.setReviewedBy(reviewedBy);
			request.setReviewedAt(Instant.now());

			refundRequestRepository.save(request);

			throw new BusinessException(ErrorCode.REFUND_FAILED, "Refund failed: " + ex.getMessage());
		}
	}

	public enum RecoveryOutcome {
		/** Razorpay confirmed the refund exists -- local state has been corrected to COMPLETED. */
		ALREADY_REFUNDED,
		/** Razorpay confirms no matching refund exists -- back to PENDING_REVIEW, safe to approve() again. */
		RETRY_ELIGIBLE,
		/** The provider could not be reached/queried -- status is unchanged, try verification again later. */
		VERIFICATION_INCONCLUSIVE
	}

	public record RecoveryResult(RefundRequest request, RecoveryOutcome outcome, String message) {
	}

	/*
	 * The ops recovery action for COMPLETED_NEEDS_VERIFICATION (see the
	 * payment recovery audit): deliberately never a generic "refund again"
	 * button. Always asks Razorpay first, then either fixes local state to
	 * match a confirmed reality (ALREADY_REFUNDED) or -- only once the
	 * provider has explicitly said no matching refund exists -- reopens the
	 * request to PENDING_REVIEW so the existing, already-authorized,
	 * already-idempotent approve() is what actually issues any retry. This
	 * method itself never calls refundPayment.
	 *
	 * Same row lock as approve()/reject(): a concurrent verifyRecovery call
	 * for the same request cannot race this one.
	 */
	@Transactional
	public RecoveryResult verifyRecovery(String id, String orgId, String reviewedBy) {
		RefundRequest request = refundRequestRepository.lockByIdAndOrgId(id, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND, "Refund request not found"));

		if (request.getStatus() != RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION) {
			throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_PROCESSED,
					"This refund request is not awaiting verification");
		}

		RazorpayPaymentService.RefundVerification verification;

		try {
			verification = razorpayPaymentService.verifyRefund(
					orgId, request.getBookingId(), request.getGatewayRefundId(), request.getRefundAmount());
		} catch (Exception ex) {
			log.warn("Refund verification could not reach Razorpay for request {}: {}", id, ex.getMessage());

			request.setReviewedBy(reviewedBy);
			request.setReviewedAt(Instant.now());
			refundRequestRepository.save(request);

			return new RecoveryResult(request, RecoveryOutcome.VERIFICATION_INCONCLUSIVE,
					"Could not verify with Razorpay right now: " + ex.getMessage() + ". Status unchanged -- try again shortly.");
		}

		if (verification.refundId() != null) {
			request.setStatus(RefundRequestStatus.COMPLETED);
			request.setGatewayRefundId(verification.refundId());
			request.setFailureReason(null);
			request.setReviewedBy(reviewedBy);
			request.setReviewedAt(Instant.now());

			refundRequestRepository.save(request);
			publishRefundCompleted(orgId, request);

			return new RecoveryResult(request, RecoveryOutcome.ALREADY_REFUNDED,
					"Razorpay confirms this refund already exists. Marked as completed.");
		}

		request.setStatus(RefundRequestStatus.PENDING_REVIEW);
		request.setGatewayRefundId(null);
		request.setReviewedBy(reviewedBy);
		request.setReviewedAt(Instant.now());

		refundRequestRepository.save(request);

		return new RecoveryResult(request, RecoveryOutcome.RETRY_ELIGIBLE,
				"Razorpay confirms no matching refund exists. This request is pending review again and can be approved.");
	}

	/* P1.7 -- same lock as approve(), so a reject() racing an approve() (or another reject()) for the same id can't both act on a stale PENDING_REVIEW read. */
	@Transactional
	public RefundRequest reject(String id, String orgId, String reviewedBy, String reason) {
		RefundRequest request = refundRequestRepository.lockByIdAndOrgId(id, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND, "Refund request not found"));

		if (request.getStatus() != RefundRequestStatus.PENDING_REVIEW) {
			throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_PROCESSED, "This refund request was already processed");
		}

		request.setStatus(RefundRequestStatus.REJECTED);
		request.setFailureReason(reason);
		request.setReviewedBy(reviewedBy);
		request.setReviewedAt(Instant.now());

		return refundRequestRepository.save(request);
	}

	private void publishRefundCompleted(String orgId, RefundRequest request) {
		Booking booking = bookingRepository.findByBookingIdAndOrgId(request.getBookingId(), orgId).orElse(null);

		if (booking == null) {
			return;
		}

		RefundCompletedEvent event = refundEventAssembler.toRefundCompletedEvent(booking);
		eventPublisher.publishEvent(event);
	}
}

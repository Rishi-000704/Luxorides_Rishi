package com.core.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.events.RefundCompletedEvent;
import com.core.events.assembler.RefundEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Booking;
import com.core.models.RefundRequest;
import com.core.models.enums.RefundRequestStatus;
import com.core.repositories.BookingRepository;
import com.core.repositories.RefundRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
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
		} catch (Exception ex) {
			request.setStatus(RefundRequestStatus.FAILED);
			request.setFailureReason(ex.getMessage());
			request.setReviewedBy(reviewedBy);
			request.setReviewedAt(Instant.now());

			refundRequestRepository.save(request);

			throw new BusinessException(ErrorCode.REFUND_FAILED, "Refund failed: " + ex.getMessage());
		}
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

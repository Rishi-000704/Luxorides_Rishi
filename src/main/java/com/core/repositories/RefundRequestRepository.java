package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.RefundRequest;
import com.core.models.enums.RefundRequestStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, String> {

	List<RefundRequest> findByOrgIdOrderByCreatedAtDesc(String orgId);

	List<RefundRequest> findByOrgIdAndStatusOrderByCreatedAtDesc(String orgId, RefundRequestStatus status);

	Optional<RefundRequest> findByIdAndOrgId(String id, String orgId);

	Optional<RefundRequest> findFirstByOrgIdAndBookingIdOrderByCreatedAtDesc(String orgId, String bookingId);

	/*
	 * P1.7 -- closes a confirmed race in RefundRequestService.approve/reject:
	 * without a lock, two concurrent approve() calls for the SAME request id
	 * (an admin double-clicking Approve, or two admins acting at once) could
	 * both read the row while status is still PENDING_REVIEW, both pass the
	 * status guard, and both fire a real razorpayPaymentService.refundPayment
	 * call. Same lock-the-row-before-mutating pattern already used for
	 * Booking/Payment/Estimate (see BookingRepository.lockByBookingIdAndOrgId,
	 * PaymentRepository.lockByGatewayOrderId).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM RefundRequest r WHERE r.id = :id AND r.orgId = :orgId")
	Optional<RefundRequest> lockByIdAndOrgId(@Param("id") String id, @Param("orgId") String orgId);
}

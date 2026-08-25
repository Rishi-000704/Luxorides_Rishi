package com.core.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.core.models.Payment;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface PaymentRepository
		extends JpaRepository<Payment, String> {

	Optional<Payment> findByIdAndOrgId(
			String id,
			String orgId);

	Optional<Payment> findByGatewayOrderId(
			String gatewayOrderId);

	@Query("""
		SELECT COALESCE(SUM(p.receivedAmount.amount), 0)
		FROM Payment p
		WHERE p.orgId = :orgId
		  AND p.status = com.core.models.enums.PaymentStatus.CONFIRMED
		  AND (:from IS NULL OR p.transactionDate >= :from)
		  AND (:to IS NULL OR p.transactionDate <= :to)
	""")
	java.math.BigDecimal sumConfirmedByOrgIdAndDateRange(
			@Param("orgId") String orgId,
			@Param("from") Instant from,
			@Param("to") Instant to
	);

	boolean existsByBooking_BookingIdAndStatus(
			String bookingId,
			PaymentStatus status);

	Optional<Payment> findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
			String orgId,
			String bookingId,
			String collectionContext,
			String collectionContextId);

	Optional<Payment> findByGatewayQrCodeId(
			String gatewayQrCodeId);

	boolean existsByGatewayPaymentId(
			String gatewayPaymentId);

	boolean existsByGatewayPaymentIdAndIdNot(
			String gatewayPaymentId,
			String id);

	Optional<Payment> findFirstByOrgIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
			String orgId,
			String collectionContext,
			String collectionContextId);

	Optional<Payment> findByGatewayOrderIdAndEstimate_Id(
			String gatewayOrderId,
			String estimateId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select p
			from Payment p
			where p.gatewayOrderId = :gatewayOrderId
			  and p.estimate.id = :estimateId
			""")
	Optional<Payment> lockByGatewayOrderIdAndEstimateId(
			@Param("gatewayOrderId")
			String gatewayOrderId,

			@Param("estimateId")
			String estimateId);

	Optional<Payment> findTopByEstimate_IdOrderByCreatedAtDesc(
			String estimateId);

	Optional<Payment> findTopByOrgIdAndEstimate_IdAndStatusInOrderByCreatedAtDesc(
			String orgId,
			String estimateId,
			Collection<PaymentStatus> statuses);

	boolean existsByOrgIdAndEstimate_IdAndStatus(
			String orgId,
			String estimateId,
			PaymentStatus status);

	List<Payment> findAllByOrgIdAndEstimate_IdOrderByCreatedAtAsc(
			String orgId,
			String estimateId);

	List<Payment> findAllByCollectionContextAndStatusAndGatewayAndExpiresAtAfter(
			String collectionContext,
			PaymentStatus status,
			PaymentGateway gateway,
			Instant expiresAt);
}
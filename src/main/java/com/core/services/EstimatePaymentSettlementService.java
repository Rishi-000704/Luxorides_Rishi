package com.core.services;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Estimate;
import com.core.models.EstimateAccessToken;
import com.core.models.Payment;
import com.core.models.enums.EstimateLinkStatus;
import com.core.models.enums.EstimateStatus;
import com.core.models.enums.PaymentMode;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.EstimateAccessTokenRepository;
import com.core.repositories.EstimateRepository;
import com.core.repositories.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstimatePaymentSettlementService {

    private final EstimateRepository estimateRepository;
    private final EstimateAccessTokenRepository tokenRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW)
    public SettlementResult confirmPayment(

            String estimateId,

            String orgId,

            String tokenId,

            String orderId,

            String razorpayPaymentId,

            String signature,

            PaymentMode paymentMode,

            Instant transactionDate) {

        Estimate estimate = estimateRepository
                .lockByIdAndOrgId(
                        estimateId,
                        orgId)
                .orElseThrow(() ->
                        new NotFoundException(
                                ErrorCode.ESTIMATE_NOT_FOUND,
                                "Estimate not found"));

        Payment payment = paymentRepository
                .lockByGatewayOrderIdAndEstimateId(
                        orderId,
                        estimateId)
                .orElseThrow(() ->
                        new NotFoundException(
                                ErrorCode.PAYMENT_NOT_FOUND,
                                "Payment not found"));

        if (!orgId.equals(payment.getOrgId())) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Payment organization mismatch");
        }

        /*
         * Make verification idempotent.
         */
        if (payment.getStatus()
                == PaymentStatus.CONFIRMED) {

            return new SettlementResult(
                    payment.getId(),
                    estimate.getPaidAt(),
                    estimate.getStatus());
        }

        /*
         * Prevent two different gateway payments from
         * settling the same estimate.
         */
        Payment alreadyConfirmed = paymentRepository
                .findTopByOrgIdAndEstimate_IdAndStatusInOrderByCreatedAtDesc(
                        orgId,
                        estimateId,
                        List.of(
                                PaymentStatus.CONFIRMED))
                .orElse(null);

        if (alreadyConfirmed != null
                && !alreadyConfirmed.getId()
                .equals(payment.getId())) {

            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Estimate is already paid using another payment");
        }

        /*
         * Prevent one Razorpay payment ID from being attached
         * to multiple Fleetovo payment records.
         */
        if (paymentRepository
                .existsByGatewayPaymentIdAndIdNot(
                        razorpayPaymentId,
                        payment.getId())) {

            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Razorpay payment is already linked to another Fleetovo payment");
        }

        payment.setGatewayPaymentId(
                razorpayPaymentId);

        payment.setGatewaySignature(
                signature);

        payment.setTransactionNumber(
                razorpayPaymentId);

        payment.setTransactionDate(
                transactionDate == null
                        ? Instant.now()
                        : transactionDate);

        payment.setPaymentMode(
                paymentMode == null
                        ? PaymentMode.UNKNOWN
                        : paymentMode);

        payment.setStatus(
                PaymentStatus.CONFIRMED);

        paymentRepository.save(payment);

        Instant paidAt =
                estimate.getPaidAt() == null
                        ? Instant.now()
                        : estimate.getPaidAt();

        if (estimate.getStatus()
                != EstimateStatus.CONVERTED) {

            estimate.setStatus(
                    EstimateStatus.PAID);
        }

        estimate.setPaidAt(paidAt);
        estimateRepository.save(estimate);

        EstimateAccessToken token =
                tokenRepository.findById(tokenId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.ACCESS_DENIED,
                                        "Estimate link not found"));

        token.setStatus(
                EstimateLinkStatus.USED);

        token.setPaidAt(paidAt);

        tokenRepository.save(token);

        return new SettlementResult(
                payment.getId(),
                paidAt,
                estimate.getStatus());
    }

    public record SettlementResult(

            String paymentId,

            Instant paidAt,

            EstimateStatus estimateStatus) {
    }
}
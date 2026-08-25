package com.core.services;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Payment;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * Replaces the driver app's payment-status poll as the only thing that ever
 * asks Razorpay whether a driver-duty QR was paid. RazorpayPaymentService's
 * isPaidByQR/confirmQrPayment (and the PaymentConfirmedEvent they publish)
 * were previously only reachable from that poll endpoint -- removing the
 * client poll without this job would leave real QR payments unconfirmed
 * forever, since nothing else ever calls Razorpay's status API.
 *
 * MOCK-gateway QR payments are auto-confirmed at creation time (see
 * MockPaymentService.generateMockQr) so they never appear in this query --
 * this job only ever touches real RAZORPAY payments still INITIATED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DutyPaymentReconciliationJob {

	private static final String DRIVER_DUTY_QR_CONTEXT = "DRIVER_DUTY_QR";

	private final PaymentRepository paymentRepository;
	private final RazorpayPaymentService razorpayPaymentService;

	@Scheduled(fixedDelay = 7000)
	public void reconcileOutstandingQrPayments() {
		List<Payment> outstanding = paymentRepository
				.findAllByCollectionContextAndStatusAndGatewayAndExpiresAtAfter(
						DRIVER_DUTY_QR_CONTEXT, PaymentStatus.INITIATED, PaymentGateway.RAZORPAY, Instant.now());

		for (Payment payment : outstanding) {
			try {
				razorpayPaymentService.isPaidByQR(
						payment.getOrgId(),
						payment.getBooking().getBookingId(),
						payment.getCollectionContextId());
			} catch (Exception ex) {
				log.warn("Duty payment reconciliation failed for payment {}: {}", payment.getId(), ex.getMessage());
			}
		}
	}
}

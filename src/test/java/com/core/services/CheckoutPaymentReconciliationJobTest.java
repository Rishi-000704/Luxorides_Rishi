package com.core.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Payment;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.PaymentRepository;

/*
 * Covers P1C/P1G: the job must attempt reconciliation for every eligible
 * candidate, attempt the expiry check afterward, and -- critically for
 * multi-instance safety -- a failure reconciling one payment (e.g. a
 * transient Razorpay timeout) must not stop the loop from processing the
 * rest. The actual confirm/expire correctness and the row-locking that makes
 * concurrent instances safe live in RazorpayPaymentService and are covered by
 * RazorpayPaymentServiceTest; RazorpayPaymentService itself is mocked here.
 */
class CheckoutPaymentReconciliationJobTest {

	private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
	private final RazorpayPaymentService razorpayPaymentService = mock(RazorpayPaymentService.class);
	private final CheckoutPaymentReconciliationJob job =
			new CheckoutPaymentReconciliationJob(paymentRepository, razorpayPaymentService);

	private Payment checkoutPayment(String id, String orgId, String orderId) {
		Payment payment = new Payment();
		payment.setId(id);
		payment.setOrgId(orgId);
		payment.setGatewayOrderId(orderId);
		payment.setStatus(PaymentStatus.INITIATED);
		return payment;
	}

	@Test
	void reconcile_queriesEligibleCheckoutPayments_andReconcilesEach() throws Exception {
		Payment a = checkoutPayment("p1", "org-1", "order_a");
		Payment b = checkoutPayment("p2", "org-1", "order_b");

		when(paymentRepository.findAllByGatewayAndStatusAndBookingIsNotNullAndCollectionContextIsNullAndCreatedAtAfter(
				eq(PaymentGateway.RAZORPAY), eq(PaymentStatus.INITIATED), any(Instant.class)))
				.thenReturn(List.of(a, b));

		job.reconcileOutstandingCheckoutPayments();

		verify(razorpayPaymentService, times(1)).reconcileByGatewayOrderId("org-1", "order_a");
		verify(razorpayPaymentService, times(1)).reconcileByGatewayOrderId("org-1", "order_b");
		verify(razorpayPaymentService, times(1)).expireIfStillInitiatedPastGracePeriod(eq("order_a"), any(Instant.class));
		verify(razorpayPaymentService, times(1)).expireIfStillInitiatedPastGracePeriod(eq("order_b"), any(Instant.class));
	}

	@Test
	void reconcile_continuesToNextPayment_whenOneReconciliationFails() throws Exception {
		Payment failing = checkoutPayment("p1", "org-1", "order_fails");
		Payment ok = checkoutPayment("p2", "org-1", "order_ok");

		when(paymentRepository.findAllByGatewayAndStatusAndBookingIsNotNullAndCollectionContextIsNullAndCreatedAtAfter(
				any(), any(), any(Instant.class)))
				.thenReturn(List.of(failing, ok));

		doThrow(new RuntimeException("Razorpay timeout"))
				.when(razorpayPaymentService).reconcileByGatewayOrderId("org-1", "order_fails");

		assertDoesNotThrow(job::reconcileOutstandingCheckoutPayments);

		verify(razorpayPaymentService, times(1)).reconcileByGatewayOrderId("org-1", "order_ok");
		verify(razorpayPaymentService, times(1)).expireIfStillInitiatedPastGracePeriod(eq("order_ok"), any(Instant.class));
		// The failing payment's own expiry check is skipped for this run -- it will be retried next run.
		verify(razorpayPaymentService, never()).expireIfStillInitiatedPastGracePeriod(eq("order_fails"), any(Instant.class));
	}

	@Test
	void reconcile_continuesToNextPayment_whenExpiryCheckFails() throws Exception {
		Payment a = checkoutPayment("p1", "org-1", "order_a");
		Payment b = checkoutPayment("p2", "org-1", "order_b");

		when(paymentRepository.findAllByGatewayAndStatusAndBookingIsNotNullAndCollectionContextIsNullAndCreatedAtAfter(
				any(), any(), any(Instant.class)))
				.thenReturn(List.of(a, b));

		doThrow(new RuntimeException("db hiccup"))
				.when(razorpayPaymentService).expireIfStillInitiatedPastGracePeriod(eq("order_a"), any(Instant.class));

		assertDoesNotThrow(job::reconcileOutstandingCheckoutPayments);

		verify(razorpayPaymentService, times(1)).reconcileByGatewayOrderId("org-1", "order_b");
		verify(razorpayPaymentService, times(1)).expireIfStillInitiatedPastGracePeriod(eq("order_b"), any(Instant.class));
	}

	@Test
	void reconcile_skipsPayments_withNoGatewayOrderId() throws Exception {
		Payment noOrderId = checkoutPayment("p1", "org-1", null);

		when(paymentRepository.findAllByGatewayAndStatusAndBookingIsNotNullAndCollectionContextIsNullAndCreatedAtAfter(
				any(), any(), any(Instant.class)))
				.thenReturn(List.of(noOrderId));

		job.reconcileOutstandingCheckoutPayments();

		verify(razorpayPaymentService, never()).reconcileByGatewayOrderId(anyString(), anyString());
	}
}

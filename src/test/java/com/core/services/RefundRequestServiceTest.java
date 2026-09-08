package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.core.events.RefundCompletedEvent;
import com.core.events.assembler.RefundEventAssembler;
import com.core.exception.BusinessException;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Booking;
import com.core.models.RefundRequest;
import com.core.models.enums.RefundRequestStatus;
import com.core.repositories.BookingRepository;
import com.core.repositories.RefundRequestRepository;

/*
 * P1.7 -- approve()/reject() previously read the RefundRequest row via a
 * plain findByIdAndOrgId. Two concurrent approve() calls for the SAME
 * request id (an admin double-click, or two admins acting at once) could
 * both read the row while status was still PENDING_REVIEW, both pass the
 * status guard, and both fire a real razorpayPaymentService.refundPayment
 * call -- a genuine double-refund risk. Now both methods lock the row first
 * (lockByIdAndOrgId), the same pattern already proven for
 * Booking/Payment/Estimate elsewhere in this codebase.
 */
class RefundRequestServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String REQUEST_ID = "refund-1";
	private static final String REVIEWER = "employee-1";

	private RefundRequestRepository refundRequestRepository;
	private BookingRepository bookingRepository;
	private RazorpayPaymentService razorpayPaymentService;
	private RefundEventAssembler refundEventAssembler;
	private ApplicationEventPublisher eventPublisher;
	private RefundRequestService service;

	@BeforeEach
	void setUp() {
		refundRequestRepository = mock(RefundRequestRepository.class);
		bookingRepository = mock(BookingRepository.class);
		razorpayPaymentService = mock(RazorpayPaymentService.class);
		refundEventAssembler = mock(RefundEventAssembler.class);
		eventPublisher = mock(ApplicationEventPublisher.class);

		service = new RefundRequestService(
				refundRequestRepository, bookingRepository, razorpayPaymentService, refundEventAssembler, eventPublisher);

		when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private RefundRequest pendingRequest(BigDecimal refundAmount) {
		RefundRequest r = new RefundRequest();
		r.setId(REQUEST_ID);
		r.setOrgId(ORG_ID);
		r.setBookingId(BOOKING_ID);
		r.setPaidAmount(refundAmount);
		r.setRefundAmount(refundAmount);
		r.setStatus(RefundRequestStatus.PENDING_REVIEW);
		return r;
	}

	@Test
	void approve_locksTheRow_notPlainFind() throws Exception {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));
		when(razorpayPaymentService.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("500.00")))
				.thenReturn("rfnd_abc");
		when(bookingRepository.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.empty());

		service.approve(REQUEST_ID, ORG_ID, REVIEWER);

		verify(refundRequestRepository, times(1)).lockByIdAndOrgId(REQUEST_ID, ORG_ID);
		verify(refundRequestRepository, never()).findByIdAndOrgId(any(), any());
	}

	@Test
	void approve_success_marksCompletedAndStoresGatewayRefundId() throws Exception {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));
		when(razorpayPaymentService.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("500.00")))
				.thenReturn("rfnd_abc");

		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		when(bookingRepository.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(refundEventAssembler.toRefundCompletedEvent(booking)).thenReturn(mock(RefundCompletedEvent.class));

		RefundRequest result = service.approve(REQUEST_ID, ORG_ID, REVIEWER);

		assertEquals(RefundRequestStatus.COMPLETED, result.getStatus());
		assertEquals("rfnd_abc", result.getGatewayRefundId());
		assertEquals(REVIEWER, result.getReviewedBy());
		verify(eventPublisher, times(1)).publishEvent(any(RefundCompletedEvent.class));
	}

	/*
	 * Simulates the race this checkpoint closes: the SAME (now-mutated)
	 * RefundRequest instance is what a second concurrent call would see once
	 * the lock forces it to wait for the first call's write -- proving the
	 * PENDING_REVIEW guard rejects the second attempt instead of firing a
	 * second real refund.
	 */
	@Test
	void approve_secondCallOnAnAlreadyApprovedRequest_rejectsWithoutRefundingAgain() throws Exception {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));
		when(razorpayPaymentService.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("500.00")))
				.thenReturn("rfnd_abc");
		when(bookingRepository.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.empty());

		service.approve(REQUEST_ID, ORG_ID, REVIEWER);

		assertThrows(BusinessException.class, () -> service.approve(REQUEST_ID, ORG_ID, REVIEWER));

		verify(razorpayPaymentService, times(1)).refundPayment(any(), any(), any());
	}

	@Test
	void approve_alreadyCompletedRequest_rejectsWithoutCallingRazorpay() throws Exception {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		request.setStatus(RefundRequestStatus.COMPLETED);
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));

		assertThrows(BusinessException.class, () -> service.approve(REQUEST_ID, ORG_ID, REVIEWER));

		verify(razorpayPaymentService, never()).refundPayment(any(), any(), any());
	}

	@Test
	void approve_zeroRefundAmount_rejectsWithoutCallingRazorpay() throws Exception {
		RefundRequest request = pendingRequest(BigDecimal.ZERO);
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));

		assertThrows(BusinessException.class, () -> service.approve(REQUEST_ID, ORG_ID, REVIEWER));

		verify(razorpayPaymentService, never()).refundPayment(any(), any(), any());
	}

	@Test
	void approve_razorpayFailure_marksFailed_andPropagatesError() throws Exception {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));
		when(razorpayPaymentService.refundPayment(any(), any(), any()))
				.thenThrow(new IllegalStateException("No confirmed gateway payment found on this booking to refund"));

		assertThrows(BusinessException.class, () -> service.approve(REQUEST_ID, ORG_ID, REVIEWER));

		assertEquals(RefundRequestStatus.FAILED, request.getStatus());
		verify(eventPublisher, never()).publishEvent(any());
	}

	/*
	 * P1H -- when the Razorpay refund itself succeeded but only local
	 * persistence failed (RazorpayPaymentService.refundPayment surfaces this as
	 * RefundPersistenceException carrying the real refund id), this must never
	 * be recorded as plain FAILED: FAILED elsewhere means "nothing happened,
	 * safe to reconsider", and conflating the two could lead someone reviewing
	 * this row to believe no refund occurred when one actually did.
	 */
	@Test
	void approve_refundPersistenceFailure_marksCompletedNeedsVerification_preservingRefundId() throws Exception {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));
		when(razorpayPaymentService.refundPayment(ORG_ID, BOOKING_ID, new BigDecimal("500.00")))
				.thenThrow(new com.core.gateway.razerpay.RefundPersistenceException("rfnd_real", new RuntimeException("db down")));

		assertThrows(BusinessException.class, () -> service.approve(REQUEST_ID, ORG_ID, REVIEWER));

		assertEquals(RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION, request.getStatus());
		assertEquals("rfnd_real", request.getGatewayRefundId());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void reject_locksTheRow_notPlainFind() {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));

		service.reject(REQUEST_ID, ORG_ID, REVIEWER, "not eligible");

		verify(refundRequestRepository, times(1)).lockByIdAndOrgId(REQUEST_ID, ORG_ID);
		verify(refundRequestRepository, never()).findByIdAndOrgId(any(), any());
	}

	@Test
	void reject_alreadyProcessedRequest_rejects() {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		request.setStatus(RefundRequestStatus.REJECTED);
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));

		assertThrows(BusinessException.class, () -> service.reject(REQUEST_ID, ORG_ID, REVIEWER, "already handled"));
	}

	@Test
	void approve_wrongOrg_notFound() throws Exception {
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, "other-org")).thenReturn(Optional.empty());

		assertThrows(BusinessException.class, () -> service.approve(REQUEST_ID, "other-org", REVIEWER));

		verify(razorpayPaymentService, never()).refundPayment(any(), any(), any());
	}

	/* ================= createFromCancellation: idempotency ================= */

	@Test
	void createFromCancellation_skipsCreation_whenARequestAlreadyExistsForBooking() {
		when(refundRequestRepository.findFirstByOrgIdAndBookingIdOrderByCreatedAtDesc(ORG_ID, BOOKING_ID))
				.thenReturn(Optional.of(pendingRequest(new BigDecimal("500.00"))));

		service.createFromCancellation(ORG_ID, BOOKING_ID, new BigDecimal("500.00"), BigDecimal.ZERO);

		verify(refundRequestRepository, never()).save(any(RefundRequest.class));
	}

	@Test
	void createFromCancellation_creates_whenNoExistingRequestForBooking() {
		when(refundRequestRepository.findFirstByOrgIdAndBookingIdOrderByCreatedAtDesc(ORG_ID, BOOKING_ID))
				.thenReturn(Optional.empty());

		service.createFromCancellation(ORG_ID, BOOKING_ID, new BigDecimal("500.00"), new BigDecimal("50.00"));

		verify(refundRequestRepository, times(1)).save(any(RefundRequest.class));
	}

	@Test
	void createFromCancellation_skipsCreation_whenNothingWasPaid() {
		service.createFromCancellation(ORG_ID, BOOKING_ID, BigDecimal.ZERO, BigDecimal.ZERO);

		verify(refundRequestRepository, never()).findFirstByOrgIdAndBookingIdOrderByCreatedAtDesc(any(), any());
		verify(refundRequestRepository, never()).save(any(RefundRequest.class));
	}

	/* ================= verifyRecovery ================= */

	private RefundRequest needsVerificationRequest(BigDecimal refundAmount, String gatewayRefundId) {
		RefundRequest r = new RefundRequest();
		r.setId(REQUEST_ID);
		r.setOrgId(ORG_ID);
		r.setBookingId(BOOKING_ID);
		r.setRefundAmount(refundAmount);
		r.setGatewayRefundId(gatewayRefundId);
		r.setStatus(RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION);
		return r;
	}

	@Test
	void verifyRecovery_marksCompleted_whenProviderConfirmsRefundExists() throws Exception {
		RefundRequest request = needsVerificationRequest(new BigDecimal("500.00"), "rfnd_real");
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));
		when(razorpayPaymentService.verifyRefund(ORG_ID, BOOKING_ID, "rfnd_real", new BigDecimal("500.00")))
				.thenReturn(new RazorpayPaymentService.RefundVerification("rfnd_real"));

		Booking booking = new Booking();
		when(bookingRepository.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(refundEventAssembler.toRefundCompletedEvent(booking)).thenReturn(mock(RefundCompletedEvent.class));

		RefundRequestService.RecoveryResult result = service.verifyRecovery(REQUEST_ID, ORG_ID, REVIEWER);

		assertEquals(RefundRequestService.RecoveryOutcome.ALREADY_REFUNDED, result.outcome());
		assertEquals(RefundRequestStatus.COMPLETED, request.getStatus());
		assertEquals("rfnd_real", request.getGatewayRefundId());
		verify(eventPublisher, times(1)).publishEvent(any(RefundCompletedEvent.class));
	}

	@Test
	void verifyRecovery_reopensToPendingReview_whenProviderConfirmsNoRefundExists() throws Exception {
		RefundRequest request = needsVerificationRequest(new BigDecimal("500.00"), null);
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));
		when(razorpayPaymentService.verifyRefund(ORG_ID, BOOKING_ID, null, new BigDecimal("500.00")))
				.thenReturn(new RazorpayPaymentService.RefundVerification(null));

		RefundRequestService.RecoveryResult result = service.verifyRecovery(REQUEST_ID, ORG_ID, REVIEWER);

		assertEquals(RefundRequestService.RecoveryOutcome.RETRY_ELIGIBLE, result.outcome());
		assertEquals(RefundRequestStatus.PENDING_REVIEW, request.getStatus());
		verify(eventPublisher, never()).publishEvent(any());
		// Never issues a refund itself -- only approve() (with its own guards) does.
		verify(razorpayPaymentService, never()).refundPayment(any(), any(), any());
	}

	@Test
	void verifyRecovery_keepsRecoveryState_whenProviderCannotBeReached() throws Exception {
		RefundRequest request = needsVerificationRequest(new BigDecimal("500.00"), "rfnd_real");
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));
		when(razorpayPaymentService.verifyRefund(any(), any(), any(), any()))
				.thenThrow(new RuntimeException("Razorpay API timeout"));

		RefundRequestService.RecoveryResult result = service.verifyRecovery(REQUEST_ID, ORG_ID, REVIEWER);

		assertEquals(RefundRequestService.RecoveryOutcome.VERIFICATION_INCONCLUSIVE, result.outcome());
		assertEquals(RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION, request.getStatus());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void verifyRecovery_rejects_whenRequestIsNotInRecoveryState() {
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, ORG_ID)).thenReturn(Optional.of(request));

		assertThrows(BusinessException.class, () -> service.verifyRecovery(REQUEST_ID, ORG_ID, REVIEWER));
	}

	@Test
	void verifyRecovery_wrongOrg_notFound() {
		when(refundRequestRepository.lockByIdAndOrgId(REQUEST_ID, "other-org")).thenReturn(Optional.empty());

		assertThrows(BusinessException.class, () -> service.verifyRecovery(REQUEST_ID, "other-org", REVIEWER));
	}

	/* ================= getOpsPage: organization isolation ================= */

	@Test
	void getOpsPage_scopesQueryToCallingOrg() {
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(refundRequestRepository.findByOrgId(ORG_ID, pageable))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

		service.getOpsPage(ORG_ID, null, pageable);

		verify(refundRequestRepository, times(1)).findByOrgId(ORG_ID, pageable);
		verify(refundRequestRepository, never()).findByOrgId(org.mockito.ArgumentMatchers.eq("other-org"), any());
	}

	@Test
	void getOpsPage_filtersByStatus_whenProvided() {
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(refundRequestRepository.findByOrgIdAndStatus(ORG_ID, RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION, pageable))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

		service.getOpsPage(ORG_ID, RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION, pageable);

		verify(refundRequestRepository, times(1))
				.findByOrgIdAndStatus(ORG_ID, RefundRequestStatus.COMPLETED_NEEDS_VERIFICATION, pageable);
		verify(refundRequestRepository, never()).findByOrgId(any(), any());
	}

	@Test
	void getOpsPage_enrichesWithCustomerReference_viaOneBatchQuery() {
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		RefundRequest request = pendingRequest(new BigDecimal("500.00"));
		when(refundRequestRepository.findByOrgId(ORG_ID, pageable))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(request)));

		com.core.models.Client client = new com.core.models.Client();
		com.core.models.embedded.Name name = new com.core.models.embedded.Name();
		name.setFirstName("Asha");
		client.setName(name);
		client.setPhone("9999999999");

		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setClient(client);

		when(bookingRepository.findAllByBookingIdInAndOrgId(java.util.List.of(BOOKING_ID), ORG_ID))
				.thenReturn(java.util.List.of(booking));

		org.springframework.data.domain.Page<com.core.dtos.payment.RefundOpsListItem> result =
				service.getOpsPage(ORG_ID, null, pageable);

		assertEquals("9999999999", result.getContent().get(0).customerPhone());
		verify(bookingRepository, times(1)).findAllByBookingIdInAndOrgId(any(), any());
	}
}

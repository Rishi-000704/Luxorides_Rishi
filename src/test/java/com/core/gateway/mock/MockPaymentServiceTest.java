package com.core.gateway.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.core.events.assembler.PaymentEventAssembler;
import com.core.gateway.razerpay.RazorpayQrPayload;
import com.core.models.Booking;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingRepository;
import com.core.repositories.PaymentRepository;
import com.core.services.ClientBookingService;

/*
 * Regression coverage for the payment-QR MySQL lock-timeout fix's second
 * requirement -- repeated/concurrent QR generation for the same duty must
 * not corrupt payment state. generateMockQr used to unconditionally insert
 * a new CONFIRMED Payment row on every call; this checks it now returns the
 * existing payment instead of minting a duplicate.
 */
class MockPaymentServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String DUTY_ID = "duty-1";

	private BookingRepository bookingRepo;
	private PaymentRepository paymentRepo;
	private MockPaymentService service;

	@BeforeEach
	void setUp() {
		bookingRepo = mock(BookingRepository.class);
		paymentRepo = mock(PaymentRepository.class);
		ClientBookingService clientBookingService = mock(ClientBookingService.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		PaymentEventAssembler paymentEventAssembler = new PaymentEventAssembler();

		service = new MockPaymentService(bookingRepo, paymentRepo, clientBookingService, eventPublisher,
				paymentEventAssembler);
	}

	@Test
	void generateMockQr_noExistingPayment_createsOne() {
		when(paymentRepo
				.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						ORG_ID, BOOKING_ID, "DRIVER_DUTY_QR", DUTY_ID))
				.thenReturn(Optional.empty());

		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		when(bookingRepo.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
			Payment payment = inv.getArgument(0);
			payment.setId("payment-1");
			return payment;
		});

		RazorpayQrPayload payload = service.generateMockQr(ORG_ID, BOOKING_ID, DUTY_ID, BigDecimal.valueOf(4500));

		assertNotNull(payload.getQrCodeId());
		assertEquals(BigDecimal.valueOf(4500), payload.getAmount());
		verify(paymentRepo, times(1)).save(any(Payment.class));
	}

	@Test
	void generateMockQr_existingPayment_returnsItWithoutCreatingAnother() {
		Payment existing = new Payment();
		existing.setId("payment-existing");
		existing.setGatewayQrCodeId("mock_qr_existing");
		existing.setReceivedAmount(Money.INR(4500f));
		existing.setStatus(PaymentStatus.CONFIRMED);
		existing.setExpiresAt(Instant.now().plus(60, ChronoUnit.MINUTES));

		when(paymentRepo
				.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						ORG_ID, BOOKING_ID, "DRIVER_DUTY_QR", DUTY_ID))
				.thenReturn(Optional.of(existing));

		RazorpayQrPayload payload = service.generateMockQr(ORG_ID, BOOKING_ID, DUTY_ID, BigDecimal.valueOf(4500));

		assertEquals("payment-existing", payload.getPaymentId());
		assertEquals("mock_qr_existing", payload.getQrCodeId());
		verify(paymentRepo, never()).save(any(Payment.class));
		verify(bookingRepo, never()).findByBookingIdAndOrgId(any(), any());
	}
}

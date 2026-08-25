package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.core.events.BookingCompletedEvent;
import com.core.events.DutyEntryCompletedEvent;
import com.core.events.assembler.BookingEventAssembler;
import com.core.events.assembler.DutyEventAssembler;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.events.assembler.RefundEventAssembler;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.GstType;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.BookingRepository;
import com.core.repositories.PackageRepository;
import com.core.mapper.BookingAssembler;
import com.core.services.common.FileService;

import org.springframework.context.ApplicationEventPublisher;

/*
 * Regression coverage for the payment-QR MySQL lock-timeout fix. The full fix
 * spans three files (see ExternalDriverDutyService.completeDutyEntryAndFinalizeBooking's
 * own comment for the complete picture); this class covers the BookingService
 * half: finalizeBookingAfterDutyCompletion recalculates the booking total and,
 * when this was the last open duty, completes the booking -- joining the
 * caller's own transaction (REQUIRED) rather than opening a separate one
 * (REQUIRES_NEW), which is what keeps the entry-completion and
 * booking-completion updates atomic with each other.
 */
class BookingServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String DUTY_ID = "duty-1";

	private BookingRepository bookingRepository;
	private ApplicationEventPublisher eventPublisher;
	private BookingService service;

	@BeforeEach
	void setUp() {
		bookingRepository = mock(BookingRepository.class);
		BookingEntryRepository bookingEntryRepository = mock(BookingEntryRepository.class);
		PackageRepository packageRepository = mock(PackageRepository.class);
		DriverService driverService = mock(DriverService.class);
		FleetVehicleService fleetVehicleService = mock(FleetVehicleService.class);
		BookingEventAssembler bookingEventAssembler = mock(BookingEventAssembler.class);
		DutyEventAssembler dutyEventAssembler = mock(DutyEventAssembler.class);
		PaymentEventAssembler paymentEventAssembler = mock(PaymentEventAssembler.class);
		RefundEventAssembler refundEventAssembler = mock(RefundEventAssembler.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		BookingAssembler bookingAssembler = mock(BookingAssembler.class);
		FileService fileService = mock(FileService.class);

		service = new BookingService(
				bookingRepository,
				bookingEntryRepository,
				packageRepository,
				driverService,
				fleetVehicleService,
				bookingEventAssembler,
				dutyEventAssembler,
				paymentEventAssembler,
				refundEventAssembler,
				eventPublisher,
				bookingAssembler,
				fileService
		);
	}

	@Test
	void finalizeBookingAfterDutyCompletion_isAnnotatedRequiredNotRequiresNew() throws NoSuchMethodException {
		Method method = BookingService.class.getMethod(
				"finalizeBookingAfterDutyCompletion", String.class, String.class, boolean.class, String.class);

		Transactional annotation = method.getAnnotation(Transactional.class);

		assertTrue(annotation != null, "finalizeBookingAfterDutyCompletion must stay @Transactional");
		assertEquals(
				Propagation.REQUIRED,
				annotation.propagation(),
				"Must stay REQUIRED (the default), not REQUIRES_NEW -- this method is only ever called "
						+ "from ExternalDriverDutyService.completeDutyEntryAndFinalizeBooking's own "
						+ "transaction, and must join it rather than open a separate one: a separate "
						+ "transaction reading the just-updated duty entry fresh under REPEATABLE READ "
						+ "can't see the caller's still-uncommitted update, and would also let the booking "
						+ "completion commit independently of (and inconsistently with) that entry update "
						+ "if anything the caller does afterward fails. Both were reproduced live against "
						+ "the dev backend before this method's propagation was fixed."
		);
	}

	@Test
	void finalizeBookingAfterDutyCompletion_lastOpenDuty_completesBooking() {
		Booking booking = bookingWithOneEntry(BookingStatus.RUNNING, DutyStatus.COMPLETED);
		when(bookingRepository.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
		when(bookingRepository.findByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		Booking result = service.finalizeBookingAfterDutyCompletion(BOOKING_ID, DUTY_ID, false, ORG_ID);

		assertEquals(BookingStatus.COMPLETED, result.getStatus());
		verify(eventPublisher).publishEvent(isA(DutyEntryCompletedEvent.class));
		verify(eventPublisher).publishEvent(isA(BookingCompletedEvent.class));
	}

	@Test
	void finalizeBookingAfterDutyCompletion_moreOpenDuties_marksRunningWithoutCompleting() {
		Booking booking = bookingWithOneEntry(BookingStatus.CONFIRMED, DutyStatus.RUNNING);
		when(bookingRepository.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

		Booking result = service.finalizeBookingAfterDutyCompletion(BOOKING_ID, DUTY_ID, true, ORG_ID);

		assertEquals(BookingStatus.RUNNING, result.getStatus());
		// DutyEntryCompletedEvent is published unconditionally -- this is the
		// broadcast-completeness fix for the "other duties still open" path,
		// which previously published nothing at all.
		verify(eventPublisher).publishEvent(isA(DutyEntryCompletedEvent.class));
		verify(eventPublisher, never()).publishEvent(isA(BookingCompletedEvent.class));
		// completeBooking() re-fetches via findByBookingIdAndOrgId only on the
		// !anyOpenDuty path -- confirm that path was never taken.
		verify(bookingRepository, never()).findByBookingIdAndOrgId(eq(BOOKING_ID), eq(ORG_ID));
	}

	@Test
	void finalizeBookingAfterDutyCompletion_lastOpenDuty_trustsCallerAnyOpenDutyOverStaleEntryRead() {
		// Simulates the real MariaDB race this regresses: submitEnd's own
		// transaction flips the entry to COMPLETED in memory (and computes
		// anyOpenDuty=false from that) but hasn't committed yet when this
		// REQUIRES_NEW method runs on a different connection, so a fresh read
		// of the entries here still sees the pre-update, non-COMPLETED status.
		// finalizeBookingAfterDutyCompletion must trust the caller's
		// anyOpenDuty flag rather than re-derive completion from that stale
		// read -- otherwise it wrongly throws DUTIES_NOT_COMPLETED, exactly
		// as reproduced live against the dev backend.
		Booking booking = bookingWithOneEntry(BookingStatus.RUNNING, DutyStatus.RUNNING);
		when(bookingRepository.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

		Booking result = service.finalizeBookingAfterDutyCompletion(BOOKING_ID, DUTY_ID, false, ORG_ID);

		assertEquals(BookingStatus.COMPLETED, result.getStatus());
		verify(eventPublisher).publishEvent(isA(DutyEntryCompletedEvent.class));
		verify(eventPublisher).publishEvent(isA(BookingCompletedEvent.class));
	}

	@Test
	void finalizeBookingAfterDutyCompletion_alreadyCompleted_isIdempotent() {
		Booking booking = bookingWithOneEntry(BookingStatus.COMPLETED, DutyStatus.COMPLETED);
		when(bookingRepository.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));
		when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

		Booking result = service.finalizeBookingAfterDutyCompletion(BOOKING_ID, DUTY_ID, false, ORG_ID);

		assertEquals(BookingStatus.COMPLETED, result.getStatus());
		assertFalse(result == null);
		verify(eventPublisher).publishEvent(isA(DutyEntryCompletedEvent.class));
		verify(eventPublisher, never()).publishEvent(isA(BookingCompletedEvent.class));
	}

	private Booking bookingWithOneEntry(BookingStatus bookingStatus, DutyStatus entryStatus) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setStatus(bookingStatus);
		booking.setTotal(Money.INR(0f));
		booking.setDiscount(Money.INR(0f));
		booking.setGstSnapshot(GstSnapshot.of(GstType.EXEMPT, java.math.BigDecimal.ZERO, 0));

		BookingEntry entry = new BookingEntry();
		entry.setStatus(entryStatus);
		entry.setDutyTotal(Money.INR(4500f));
		booking.setEntries(List.of(entry));

		return booking;
	}
}

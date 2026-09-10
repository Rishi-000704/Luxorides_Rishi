package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.booking.AllotDutyCommand;
import com.core.events.assembler.BookingEventAssembler;
import com.core.events.assembler.DutyEventAssembler;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.events.assembler.RefundEventAssembler;
import com.core.exception.BusinessException;
import com.core.mapper.BookingAssembler;
import com.core.models.AssignmentHistory;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.GstType;
import com.core.repositories.AssignmentHistoryRepository;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.BookingRepository;
import com.core.repositories.PackageRepository;
import com.core.services.common.FileService;

/*
 * Phase C -- historical assignment record persisted by BookingService.
 * reAllotDuty whenever a duty's driverId and/or fleetVehicleId actually
 * changes from its current (non-null, since reAllotDuty only ever runs on
 * an already-allotted duty) value. allotDuty (the initial REQUESTED ->
 * ALLOTTED assignment) deliberately does NOT create a row -- see
 * AssignmentHistory's own class comment for why. These tests verify actual
 * persisted field values via an ArgumentCaptor, not merely that save() was
 * called.
 */
class BookingServiceAssignmentHistoryTest {

	private static final String ORG_ID = "org-1";
	private static final String BOOKING_ID = "booking-1";
	private static final String DUTY_ID = "duty-1";

	private BookingRepository bookingRepository;
	private BookingEntryRepository bookingEntryRepository;
	private AssignmentHistoryRepository assignmentHistoryRepository;
	private DriverService driverService;
	private FleetVehicleService fleetVehicleService;
	private DutyEventAssembler dutyEventAssembler;
	private BookingService service;

	@BeforeEach
	void setUp() {
		bookingRepository = mock(BookingRepository.class);
		bookingEntryRepository = mock(BookingEntryRepository.class);
		PackageRepository packageRepository = mock(PackageRepository.class);
		driverService = mock(DriverService.class);
		fleetVehicleService = mock(FleetVehicleService.class);
		BookingEventAssembler bookingEventAssembler = mock(BookingEventAssembler.class);
		dutyEventAssembler = mock(DutyEventAssembler.class);
		PaymentEventAssembler paymentEventAssembler = mock(PaymentEventAssembler.class);
		RefundEventAssembler refundEventAssembler = mock(RefundEventAssembler.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		BookingAssembler bookingAssembler = mock(BookingAssembler.class);
		FileService fileService = mock(FileService.class);
		assignmentHistoryRepository = mock(AssignmentHistoryRepository.class);

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
				fileService,
				assignmentHistoryRepository
		);

		when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
		when(bookingEntryRepository.save(any(BookingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private Booking bookingWithEntry(DutyStatus dutyStatus, String driverId, String fleetVehicleId) {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setStatus(BookingStatus.CONFIRMED);
		booking.setTotal(Money.INR(0f));
		booking.setDiscount(Money.INR(0f));
		booking.setGstSnapshot(GstSnapshot.of(GstType.EXEMPT, java.math.BigDecimal.ZERO, 0));

		BookingEntry entry = new BookingEntry();
		entry.setId("entry-1");
		entry.setDutyId(DUTY_ID);
		entry.setStatus(dutyStatus);
		entry.setDriverId(driverId);
		entry.setFleetVehicleId(fleetVehicleId);
		entry.setDutyTotal(Money.INR(4500f));
		entry.setBooking(booking);

		List<BookingEntry> entries = new ArrayList<>();
		entries.add(entry);
		booking.setEntries(entries);

		when(bookingRepository.lockByBookingIdAndOrgId(BOOKING_ID, ORG_ID)).thenReturn(Optional.of(booking));

		return booking;
	}

	private AllotDutyCommand cmd(String driverId, String fleetVehicleId) {
		return new AllotDutyCommand(BOOKING_ID, DUTY_ID, "supplier-1", driverId, fleetVehicleId);
	}

	/* ===================== 1. INITIAL ASSIGNMENT ===================== */

	@Test
	void initialAssignment_allotDuty_doesNotCreateAHistoryRecord() {
		bookingWithEntry(DutyStatus.REQUESTED, null, null);

		service.allotDuty(cmd("driver-A", "vehicle-X"), ORG_ID);

		verify(assignmentHistoryRepository, never()).save(any(AssignmentHistory.class));
	}

	/* ===================== 2. DRIVER-ONLY REASSIGNMENT ===================== */

	@Test
	void driverOnlyReassignment_persistsHistoryWithVehicleUnchanged() {
		bookingWithEntry(DutyStatus.ALLOTTED, "driver-A", "vehicle-X");

		service.reAllotDuty(cmd("driver-B", "vehicle-X"), ORG_ID);

		ArgumentCaptor<AssignmentHistory> captor = ArgumentCaptor.forClass(AssignmentHistory.class);
		verify(assignmentHistoryRepository, times(1)).save(captor.capture());
		AssignmentHistory history = captor.getValue();

		assertEquals(ORG_ID, history.getOrgId());
		assertEquals(BOOKING_ID, history.getBookingId());
		assertEquals(DUTY_ID, history.getDutyId());
		assertEquals("driver-A", history.getPreviousDriverId());
		assertEquals("driver-B", history.getNewDriverId());
		assertEquals("vehicle-X", history.getPreviousFleetVehicleId());
		assertEquals("vehicle-X", history.getNewFleetVehicleId());
	}

	/* ===================== 3. VEHICLE-ONLY REASSIGNMENT ===================== */

	@Test
	void vehicleOnlyReassignment_persistsHistoryWithDriverUnchanged() {
		bookingWithEntry(DutyStatus.ALLOTTED, "driver-A", "vehicle-X");

		service.reAllotDuty(cmd("driver-A", "vehicle-Y"), ORG_ID);

		ArgumentCaptor<AssignmentHistory> captor = ArgumentCaptor.forClass(AssignmentHistory.class);
		verify(assignmentHistoryRepository, times(1)).save(captor.capture());
		AssignmentHistory history = captor.getValue();

		assertEquals("driver-A", history.getPreviousDriverId());
		assertEquals("driver-A", history.getNewDriverId());
		assertEquals("vehicle-X", history.getPreviousFleetVehicleId());
		assertEquals("vehicle-Y", history.getNewFleetVehicleId());
	}

	/* ===================== 4. DRIVER + VEHICLE REASSIGNMENT ===================== */

	@Test
	void driverAndVehicleReassignment_persistsHistoryWithBothChanged() {
		bookingWithEntry(DutyStatus.ALLOTTED, "driver-A", "vehicle-X");

		service.reAllotDuty(cmd("driver-B", "vehicle-Y"), ORG_ID);

		ArgumentCaptor<AssignmentHistory> captor = ArgumentCaptor.forClass(AssignmentHistory.class);
		verify(assignmentHistoryRepository, times(1)).save(captor.capture());
		AssignmentHistory history = captor.getValue();

		assertEquals("driver-A", history.getPreviousDriverId());
		assertEquals("driver-B", history.getNewDriverId());
		assertEquals("vehicle-X", history.getPreviousFleetVehicleId());
		assertEquals("vehicle-Y", history.getNewFleetVehicleId());
	}

	/* ===================== 5. NO-OP ASSIGNMENT ===================== */

	@Test
	void noOpReassignment_sameDriverAndVehicle_createsNoHistoryRecord() {
		bookingWithEntry(DutyStatus.ALLOTTED, "driver-A", "vehicle-X");

		service.reAllotDuty(cmd("driver-A", "vehicle-X"), ORG_ID);

		verify(assignmentHistoryRepository, never()).save(any(AssignmentHistory.class));
	}

	/* ===================== 6. MULTIPLE LEGITIMATE REASSIGNMENTS ===================== */

	@Test
	void multipleReassignments_aToBToC_preservesBothTransitions() {
		bookingWithEntry(DutyStatus.ALLOTTED, "driver-A", "vehicle-X");

		service.reAllotDuty(cmd("driver-B", "vehicle-X"), ORG_ID);
		service.reAllotDuty(cmd("driver-C", "vehicle-X"), ORG_ID);

		ArgumentCaptor<AssignmentHistory> captor = ArgumentCaptor.forClass(AssignmentHistory.class);
		verify(assignmentHistoryRepository, times(2)).save(captor.capture());
		List<AssignmentHistory> records = captor.getAllValues();

		assertEquals("driver-A", records.get(0).getPreviousDriverId());
		assertEquals("driver-B", records.get(0).getNewDriverId());
		assertEquals("driver-B", records.get(1).getPreviousDriverId());
		assertEquals("driver-C", records.get(1).getNewDriverId());
	}

	/* ===================== 7. VALIDATION-BEFORE-PERSISTENCE / ROLLBACK SAFETY ===================== */

	@Test
	void invalidDutyStatus_throwsBeforeAnyHistoryIsPersisted() {
		// REQUESTED is not in reAllotDuty's allowed set (ALLOTTED/RUNNING/
		// COMPLETED only) -- ensureDutyStatus must reject this before the
		// change-detection/history-write logic ever runs.
		bookingWithEntry(DutyStatus.REQUESTED, "driver-A", "vehicle-X");

		assertThrows(BusinessException.class, () -> service.reAllotDuty(cmd("driver-B", "vehicle-X"), ORG_ID));

		verify(assignmentHistoryRepository, never()).save(any(AssignmentHistory.class));
	}

	@Test
	void reAllotDuty_and_allotDuty_stayTransactional_soHistoryAndAssignmentCommitOrRollbackTogether()
			throws NoSuchMethodException {
		// Can't exercise a real DB rollback in a mock-based unit test (same
		// documented limitation as this suite's other @Transactional checks,
		// e.g. BookingServiceTest's REQUIRED/REQUIRES_NEW assertion) --
		// this proves the transactional boundary that makes the history save
		// and the entry/booking save atomic is still in place, the same
		// mechanism the whole rest of allotDuty/reAllotDuty already relies on.
		Method reAllot = BookingService.class.getMethod("reAllotDuty", AllotDutyCommand.class, String.class);
		Method allot = BookingService.class.getMethod("allotDuty", AllotDutyCommand.class, String.class);

		assertTrue(reAllot.isAnnotationPresent(Transactional.class), "reAllotDuty must stay @Transactional");
		assertTrue(allot.isAnnotationPresent(Transactional.class), "allotDuty must stay @Transactional");
	}

	/* ===================== 8. RETRY / IDEMPOTENCY ===================== */

	@Test
	void retryingTheSameReassignment_afterItAlreadyApplied_createsNoAdditionalHistory() {
		Booking booking = bookingWithEntry(DutyStatus.ALLOTTED, "driver-A", "vehicle-X");

		// First call: a real change, applied against the locked row (the
		// mock returns the SAME booking/entry instance on every
		// lockByBookingIdAndOrgId call, exactly matching how the real
		// pessimistic lock always hands back the current row state).
		service.reAllotDuty(cmd("driver-B", "vehicle-X"), ORG_ID);
		assertEquals("driver-B", booking.getEntries().get(0).getDriverId());

		// Retry of the same logical request (e.g. a double-tap or a replayed
		// call) -- the entry already reflects driver-B, so this must be a
		// no-op for history purposes.
		service.reAllotDuty(cmd("driver-B", "vehicle-X"), ORG_ID);

		verify(assignmentHistoryRepository, times(1)).save(any(AssignmentHistory.class));
	}

	/* ===================== 9. ORGANIZATION ISOLATION ===================== */

	@Test
	void historyRecord_isStampedWithTheRequestingOrganization_notAnyOtherOrg() {
		bookingWithEntry(DutyStatus.ALLOTTED, "driver-A", "vehicle-X");

		service.reAllotDuty(cmd("driver-B", "vehicle-X"), ORG_ID);

		ArgumentCaptor<AssignmentHistory> captor = ArgumentCaptor.forClass(AssignmentHistory.class);
		verify(assignmentHistoryRepository).save(captor.capture());
		assertEquals(ORG_ID, captor.getValue().getOrgId());

		// The read path (AssignmentHistoryRepository.findByOrgIdAndDutyIdOrderByCreatedAtAsc)
		// is a Spring Data derived query scoped by both orgId and dutyId --
		// same convention as TripRatingRepository.findByDutyIdAndOrgId -- so a
		// caller from a different org can never retrieve this row through it;
		// there is no unscoped history query anywhere in this repository.
	}

	/* ===================== 10. ACTOR / REASON PRESERVATION ===================== */

	@Test
	void assignmentHistory_extendsAuditableEntity_soCreatedByAndCreatedAtAreCapturedByExistingAuditing() {
		// AssignmentHistory relies entirely on the same AuditingEntityListener
		// / AuditorAwareImpl (SecurityContextHolder-backed) mechanism every
		// other entity in this app already uses for createdBy/createdAt --
		// not exercised end-to-end here (no Spring context in this unit
		// test, same documented limitation as this codebase's other
		// declares-only reflection tests), but structurally guaranteed by
		// inheritance.
		assertTrue(AuditableEntity.class.isAssignableFrom(AssignmentHistory.class));
	}

	@Test
	void reason_isLeftNull_noReliableSourceExistsInTheCurrentWorkflow() {
		bookingWithEntry(DutyStatus.ALLOTTED, "driver-A", "vehicle-X");

		service.reAllotDuty(cmd("driver-B", "vehicle-X"), ORG_ID);

		ArgumentCaptor<AssignmentHistory> captor = ArgumentCaptor.forClass(AssignmentHistory.class);
		verify(assignmentHistoryRepository).save(captor.capture());
		assertNull(captor.getValue().getReason(),
				"AllotDutyCommand carries no reason field today -- must not be fabricated");
	}
}

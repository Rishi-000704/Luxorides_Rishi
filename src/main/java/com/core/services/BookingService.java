package com.core.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.booking.AllotDutyCommand;
import com.core.dtos.booking.BookingDTO;
import com.core.dtos.booking.BookingForm;
import com.core.dtos.booking.BookingListItem;
import com.core.dtos.booking.BookingPaymentCommand;
import com.core.dtos.booking.CloseDutyCommand;
import com.core.dtos.booking.DutyForm;
import com.core.dtos.booking.DutyListItem;
import com.core.events.BookingCancelledEvent;
import com.core.events.BookingCompletedEvent;
import com.core.events.BookingConfirmedEvent;
import com.core.events.DutyAllottedEvent;
import com.core.events.DutyClosedEvent;
import com.core.events.DutyEntryCompletedEvent;
import com.core.events.DutyReAllottedEvent;
import com.core.events.DutyReClosedEvent;
import com.core.events.PaymentConfirmedEvent;
import com.core.events.RefundInitiatedEvent;
import com.core.events.SyncInvoiceEvent;
import com.core.events.assembler.BookingEventAssembler;
import com.core.events.assembler.DutyEventAssembler;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.events.assembler.RefundEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.mapper.BookingAssembler;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.ExtraCharge;
import com.core.models.Payment;
import com.core.models.Package;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.models.AssignmentHistory;
import com.core.repositories.AssignmentHistoryRepository;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.BookingRepository;
import com.core.repositories.PackageRepository;
import com.core.services.common.FileService;
import com.core.util.AddressUtil;
import com.core.util.BookingUtil;
import com.core.util.PackageUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.core.dtos.booking.BookingPageRequest;
import com.core.dtos.booking.DutyPageRequest;
import com.core.dtos.common.PageResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepository bookingRepository;
	private final BookingEntryRepository bookingEntryRepository;
	private final PackageRepository packageRepository;
	private final DriverService driverService;
	private final FleetVehicleService fleetVehicleService;

	private final BookingEventAssembler bookingEventAssembler;
	private final DutyEventAssembler dutyEventAssembler;
	private final PaymentEventAssembler paymentEventAssembler;
	private final RefundEventAssembler refundEventAssembler;

	private final ApplicationEventPublisher eventPublisher;
	private final BookingAssembler bookingAssembler;

	private final FileService fileService;

	private final AssignmentHistoryRepository assignmentHistoryRepository;

	/* ======================= BOOKING ================================ */

	@Transactional
	public BookingDTO addBooking(BookingForm form, String orgId) {
		Booking booking = new Booking();
		booking.setBookingId(BookingUtil.generateBookingId());
		booking.setOrgId(orgId);
		booking.setClientId(form.clientId());

		if (form.clientBillingEntityId() == null || form.clientBillingEntityId().isBlank()) {
			booking.setClientBillingEntityId(null);
		} else {
			booking.setClientBillingEntityId(form.clientBillingEntityId());
		}

		booking.setRemarks(form.remarks());
		booking.setGstSnapshot(GstSnapshot.of(form.gstType(), BigDecimal.valueOf(0), form.gstRate()));
		booking.setTotal(Money.INR(0f));
		booking.setDiscount(Money.INR(normalizeDiscount(form.discountAmount())));
		booking.setStatus(BookingStatus.DRAFT);

		return bookingAssembler.assemble(bookingRepository.save(booking));
	}

	@Transactional
	@SuppressWarnings("null")
	public BookingDTO updateBooking(BookingForm form, String orgId) {
		Booking booking = getBookingForUpdate(form.bookingId(), orgId);

		booking.setClientId(form.clientId());

		if (form.clientBillingEntityId() == null || form.clientBillingEntityId().isBlank()) {
			booking.setClientBillingEntityId(null);
		} else {
			booking.setClientBillingEntityId(form.clientBillingEntityId());
		}

		booking.setRemarks(form.remarks());
		booking.setDiscount(Money.INR(normalizeDiscount(form.discountAmount())));
		booking.setGstSnapshot(GstSnapshot.of(form.gstType(), BigDecimal.valueOf(0), form.gstRate()));

		Booking saved = bookingRepository.save(BookingUtil.calculateTotalAmount(booking));
		syncInvoiceIfGenerated(saved, orgId);

		return bookingAssembler.assemble(saved);
	}

	@Transactional
	@SuppressWarnings("null")
	public void confirmBooking(String bookingId, String orgId) {
		Booking booking = getBookingForUpdate(bookingId, orgId);

		ensureBookingStatus(
				booking,
				Set.of(BookingStatus.DRAFT),
				"confirm booking"
		);

		if (booking.getEntries() == null || booking.getEntries().isEmpty()) {
			throw new BusinessException(ErrorCode.DUTY_NOT_FOUND, "Cannot confirm booking without duties.");
		}

		booking.setStatus(BookingStatus.CONFIRMED);
		booking = bookingRepository.save(booking);

		BookingConfirmedEvent event = bookingEventAssembler.toBookingConfirmedEvent(booking);
		eventPublisher.publishEvent(event);
	}

	@Transactional
	public void cancelBooking(String bookingId, String orgId, String reason) {
		cancelBooking(bookingId, orgId, reason, BigDecimal.ZERO);
	}

	/*
	 * cancellationFeeAmount is deducted from the paid amount when
	 * RefundInitiatedListener creates the admin-reviewed RefundRequest --
	 * BigDecimal.ZERO here (the 3-arg overload above, used by the employee
	 * cancellation endpoint) means "full refund, no fee", unchanged from
	 * this method's original behavior. The client-facing cancellation flow
	 * (ClientBookingService.cancelBookingWithPolicy) is the only caller that
	 * passes a nonzero fee, computed by CancellationPolicyService.
	 */
	@Transactional
	@SuppressWarnings("null")
	public void cancelBooking(String bookingId, String orgId, String reason, BigDecimal cancellationFeeAmount) {
		Booking booking = getBookingForUpdate(bookingId, orgId);

		/*
		 * P1.7 -- getBookingForUpdate's lock makes this check race-safe: a
		 * repeated/concurrent cancel call for a booking already CANCELLED
		 * (double-tap, retry after a lost response, or a race with another
		 * concurrent cancel request) is rejected here instead of re-running
		 * the cancellation side effects, which previously included
		 * publishing a second RefundInitiatedEvent -- and therefore creating
		 * a second RefundRequest row -- for the same cancellation. Every
		 * other status-changing method on this class already guards its
		 * entry state the same way (see ensureBookingStatus); this one just
		 * hadn't. Only CANCELLED is rejected -- every other status keeps
		 * exactly the transitions it already allowed, so no legitimate
		 * existing cancellation path (employee or client) is narrowed.
		 */
		if (booking.getStatus() == BookingStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS, "Booking is already cancelled");
		}

		booking.setStatus(BookingStatus.CANCELLED);
		booking.setRemarks(reason);

		Booking saved = bookingRepository.save(booking);
		syncInvoiceIfGenerated(saved, orgId);

		BookingCancelledEvent cancelevent = bookingEventAssembler.toBookingCancelledEvent(booking, reason);
		eventPublisher.publishEvent(cancelevent);

		RefundInitiatedEvent refundevent = refundEventAssembler.toRefundInitiatedEvent(booking, cancellationFeeAmount);
		eventPublisher.publishEvent(refundevent);
	}

	@Transactional
	@SuppressWarnings("null")
	public void completeBooking(String bookingId, String orgId) {
		Booking booking = getBookingForUpdate(bookingId, orgId);

		if (!allDutiesCompleted(booking)) {
			throw new BusinessException(ErrorCode.DUTIES_NOT_COMPLETED, "Each duty must be completed.");
		}

		completeBookingInternal(booking, orgId);
	}

	/*
	 * Shared by completeBooking() above and finalizeBookingAfterDutyCompletion()
	 * below. Deliberately does NOT re-derive "are all duties completed" from a
	 * fresh read of booking.getEntries() -- unlike completeBooking()'s own check,
	 * which is safe because it always runs in a request with no other in-flight
	 * transaction touching this booking. finalizeBookingAfterDutyCompletion runs
	 * in its own REQUIRES_NEW transaction while ExternalDriverDutyService.submitEnd's
	 * transaction (which just flipped the completed entry's status) is still open
	 * and uncommitted, so a fresh read here would see the pre-update entries under
	 * REPEATABLE READ and wrongly throw DUTIES_NOT_COMPLETED. submitEnd already
	 * computed that correctly in-memory (its entry and this booking's entries
	 * collection are the same identity-mapped objects within its own persistence
	 * context) -- that's the anyOpenDuty flag callers must pass in instead.
	 */
	@SuppressWarnings("null")
	private Booking completeBookingInternal(Booking booking, String orgId) {
		if (booking.getStatus() == BookingStatus.BILLED) {
			throw new BusinessException(ErrorCode.BOOKING_ALREADY_BILLED, "Booking is already billed.");
		}

		if (booking.getStatus() == BookingStatus.COMPLETED) {
			return booking;
		}

		ensureBookingStatus(
				booking,
				Set.of(BookingStatus.CONFIRMED, BookingStatus.RUNNING),
				"complete booking"
		);

		booking.setStatus(BookingStatus.COMPLETED);
		Booking saved = bookingRepository.save(BookingUtil.calculateTotalAmount(booking));

		eventPublisher.publishEvent(new BookingCompletedEvent(saved.getBookingId(), orgId));

		return saved;
	}

	/*
	 * Called from ExternalDriverDutyService.completeDutyEntryAndFinalizeBooking,
	 * which is that class's own top-level transaction for "mark this duty entry
	 * COMPLETED and, if needed, complete the booking" -- deliberately plain
	 * REQUIRED (joins the caller), not REQUIRES_NEW. anyOpenDuty=false means the
	 * caller just flipped the last open entry's status to COMPLETED, in the SAME
	 * persistence context this call needs to see; a separate transaction
	 * (REQUIRES_NEW) would read that entry fresh from the DB under REPEATABLE
	 * READ and, since the caller's update is still uncommitted, see it as not
	 * yet completed. Booking.status=COMPLETED must also never commit unless the
	 * entry update it depends on commits too -- REQUIRES_NEW would let this
	 * commit independently and leave the two inconsistent if anything the
	 * caller does afterward (in the same transaction) then fails.
	 */
	@Transactional
	public Booking finalizeBookingAfterDutyCompletion(String bookingId, String dutyId, boolean anyOpenDuty,
			String orgId) {

		Booking booking = getBookingForUpdate(bookingId, orgId);

		eventPublisher.publishEvent(new DutyEntryCompletedEvent(bookingId, dutyId, orgId));

		if (anyOpenDuty) {
			booking.setStatus(BookingStatus.RUNNING);
			return bookingRepository.save(BookingUtil.calculateTotalAmount(booking));
		}

		return completeBookingInternal(booking, orgId);
	}

	/* ==================== BOOKING ENTRY ======================== */

	@Transactional
	@SuppressWarnings("null")
	public BookingDTO addBookingEntry(DutyForm form, String orgId) {
		Booking booking = getBookingForUpdate(form.bookingId(), orgId);


		Package pack = this.getPackage(form.packageId());

		if (pack == null) {
			throw new NotFoundException(ErrorCode.PACKAGE_NOT_FOUND, "Package not found.");
		}

		BookingEntry entry = new BookingEntry();
		entry.setBooking(booking);
		entry.setDutyId(BookingUtil.generateDutyId(booking));
		entry.setPassengerIds(form.passengerIds());
		entry.setReportingTime(form.reportingTime());
		entry.setDropTime(form.dropTime());

		entry.setReportingLocation(AddressUtil.toAddressSnapshot(form.reportingLocation()));
		entry.setDropLocation(AddressUtil.toAddressSnapshot(form.dropLocation()));
		entry.setMasterVehicleId(form.requestedVehicleId());
		entry.setFlightNumber(form.flightNumber());
		entry.setClientNotes(form.clientNotes());

		entry.setPack(PackageUtil.toPackageSnapshot(pack));
		entry.setStatus(DutyStatus.REQUESTED);
		entry.setDutyTotal(pack.getBaseFare());

		booking.getEntries().add(entry);
		reopenCompletedBookingAfterDutyAdded(booking);

		Booking saved = bookingRepository.save(BookingUtil.calculateTotalAmount(booking));
		syncInvoiceIfGenerated(saved, orgId);

		return bookingAssembler.assemble(saved);
	}

	@Transactional
	@SuppressWarnings("null")
	public BookingDTO updateBookingEntry(DutyForm form, String orgId) {
		if (form.bookingId() == null) {
			throw new BusinessException(ErrorCode.BOOKING_ID_REQUIRED, "provided booking ID cannot be null.");
		}

		Booking booking = getBookingForUpdate(form.bookingId(), orgId);


		BookingEntry entry = this.getDutyFromBooking(booking, form.dutyId());

		ensureDutyStatus(
				entry,
				Set.of(
						DutyStatus.DRAFT,
						DutyStatus.REQUESTED,
						DutyStatus.CONFIRMED,
						DutyStatus.ALLOTTED,
						DutyStatus.RUNNING,
						DutyStatus.COMPLETED
				),
				"update duty"
		);

		Package pack = this.getPackage(form.packageId());

		if (pack == null) {
			throw new NotFoundException(ErrorCode.PACKAGE_NOT_FOUND, "Package not found.");
		}

		if (entry.getPassengerIds() != null) {
			entry.getPassengerIds().clear();
		}

		entry.setPassengerIds(form.passengerIds());
		entry.setReportingTime(form.reportingTime());
		entry.setReportingLocation(AddressUtil.toAddressSnapshot(form.reportingLocation()));
		entry.setDropLocation(AddressUtil.toAddressSnapshot(form.dropLocation()));
		entry.setDropTime(form.dropTime());
		entry.setMasterVehicleId(form.requestedVehicleId());
		entry.setFlightNumber(form.flightNumber());
		entry.setClientNotes(form.clientNotes());
		entry.setPack(PackageUtil.toPackageSnapshot(pack));

		entry = BookingUtil.calculateTotal(entry);

		bookingEntryRepository.save(entry);

		Booking saved = bookingRepository.save(BookingUtil.calculateTotalAmount(booking));
		syncInvoiceIfGenerated(saved, orgId);

		return bookingAssembler.assemble(saved);
	}

	@Transactional
	@SuppressWarnings("null")
	public BookingDTO allotDuty(AllotDutyCommand cmd, String orgId) {
		Booking booking = getBookingForUpdate(cmd.bookingId(), orgId);


		BookingEntry entry = this.getDutyFromBooking(booking, cmd.dutyId());

		ensureDutyStatus(
				entry,
				Set.of(DutyStatus.REQUESTED),
				"allot duty"
		);

		entry.setDriverId(cmd.driverId());
		entry.setSupplierId(cmd.supplierId());
		entry.setFleetVehicleId(cmd.fleetVehicleId());
		entry.setStatus(DutyStatus.ALLOTTED);

		entry.setAllotedVehicle(fleetVehicleService.get(cmd.fleetVehicleId()));
		entry.setDriver(driverService.getDriver(cmd.driverId(), orgId));

		bookingEntryRepository.save(entry);

		markBookingRunningUnlessTerminal(booking);

		Booking saved = bookingRepository.save(booking);
		syncInvoiceIfGenerated(saved, orgId);

		DutyAllottedEvent event = dutyEventAssembler.toDutyAllottedEvent(booking, entry);
		eventPublisher.publishEvent(event);

		return bookingAssembler.assemble(saved);
	}

	@Transactional
	@SuppressWarnings("null")
	public BookingDTO reAllotDuty(AllotDutyCommand cmd, String orgId) {
		Booking booking = getBookingForUpdate(cmd.bookingId(), orgId);


		BookingEntry entry = this.getDutyFromBooking(booking, cmd.dutyId());

		ensureDutyStatus(
				entry,
				Set.of(DutyStatus.ALLOTTED, DutyStatus.RUNNING, DutyStatus.COMPLETED),
				"re-allot duty"
		);

		boolean driverChanged = !cmd.driverId().equals(entry.getDriverId());
		boolean vehicleChanged = !java.util.Objects.equals(cmd.fleetVehicleId(), entry.getFleetVehicleId());

		/*
		 * Phase C -- preserve the previous assignment before it's overwritten
		 * below, but only for a REAL transition. reAllotDuty only ever runs on
		 * a duty that already went through allotDuty (ensureDutyStatus above
		 * requires ALLOTTED/RUNNING/COMPLETED), so entry.getDriverId()/
		 * getFleetVehicleId() are guaranteed non-null "previous" values here --
		 * there is no null-previous edge case to handle on this path.
		 *
		 * Idempotency comes entirely from the existing architecture, not a new
		 * mechanism: getBookingForUpdate() above already takes a pessimistic
		 * lock on this booking row for the whole transaction, so two
		 * concurrent calls (a retried request, a double-tap) can't both read
		 * the pre-change state -- the second to acquire the lock sees the
		 * first's already-committed update and correctly computes
		 * driverChanged=false/vehicleChanged=false, writing no history row.
		 * Same guarantee driverChanged already relied on above for the
		 * OTP-clearing block; vehicleChanged reuses it too.
		 */
		if (driverChanged || vehicleChanged) {
			AssignmentHistory history = new AssignmentHistory();
			history.setOrgId(orgId);
			history.setBookingId(booking.getBookingId());
			history.setDutyId(entry.getDutyId());
			history.setBookingEntry(entry);
			history.setPreviousDriverId(entry.getDriverId());
			history.setNewDriverId(cmd.driverId());
			history.setPreviousFleetVehicleId(entry.getFleetVehicleId());
			history.setNewFleetVehicleId(cmd.fleetVehicleId());
			// No reliable reassignment reason exists in the current workflow --
			// AllotDutyCommand carries none (grep-verified) and no reason-capture
			// UI exists yet. Left null rather than fabricated.
			assignmentHistoryRepository.save(history);
		}

		entry.setDriverId(cmd.driverId());
		entry.setSupplierId(cmd.supplierId());
		entry.setFleetVehicleId(cmd.fleetVehicleId());

		entry.setAllotedVehicle(fleetVehicleService.get(cmd.fleetVehicleId()));
		entry.setDriver(driverService.getDriver(cmd.driverId(), orgId));

		if (driverChanged) {
			// A different driver now owns this duty -- the previous driver's
			// accept/decline decision and any pickup OTP issued to them must not
			// leak forward. The newly-assigned driver goes through their own
			// real accept step (see DriverAppService.issueExecutionToken's gate).
			entry.setDriverAcceptedAt(null);
			entry.setDriverDeclinedAt(null);
			entry.setDriverDeclineReason(null);
			entry.setPickupOtpHash(null);
			entry.setPickupOtpExpiresAt(null);
			entry.setPickupOtpAttempts(null);
			entry.setPickupOtpVerifiedAt(null);
		}

		bookingEntryRepository.save(entry);

		Booking saved = bookingRepository.save(booking);
		syncInvoiceIfGenerated(saved, orgId);

		DutyReAllottedEvent event = dutyEventAssembler.toDutyReAllottedEvent(booking, entry);
		eventPublisher.publishEvent(event);

		return bookingAssembler.assemble(saved);
	}

	/* ======================== CLOSE DUTY ============================ */

	@Transactional
	public BookingDTO closeDuty(CloseDutyCommand cmd, String orgId) {

		Booking booking = getBookingForUpdate(
				cmd.getBookingId(),
				orgId
		);


		BookingStatus previousBookingStatus = booking.getStatus();

		BookingEntry entry = getDutyFromBooking(
				booking,
				cmd.getDutyId()
		);

		ensureDutyStatus(
				entry,
				Set.of(
						DutyStatus.ALLOTTED,
						DutyStatus.RUNNING
				),
				"close duty"
		);

		entry.setReportingTime(cmd.getReportingTime());
		entry.setStartingKM(cmd.getStartingKM());
		entry.setDropTime(cmd.getDropTime());
		entry.setClosingKM(cmd.getClosingKM());
		entry.setNightChargeble(cmd.getNightChargeble());
		entry.setStartAt(cmd.getStartAt());
		entry.setEndAt(cmd.getEndAt());

		entry.setDutySlipImage(
				resolveCloseSlipImage(
						cmd.getDutySlipImageFile(),
						cmd.getDutySlipImage()
				)
		);

		entry.setStatus(DutyStatus.COMPLETED);

		if (entry.getCharges() == null) {
			entry.setCharges(new ArrayList<>());
		}

		if (cmd.getExtraCharges() != null) {

			for (CloseDutyCommand.ExtraCharge submittedCharge
					: cmd.getExtraCharges()) {

				if (submittedCharge == null) {
					throw new BusinessException(
							ErrorCode.BAD_REQUEST,
							"Extra charge details are required."
					);
				}

				String description = clean(
						submittedCharge.getDescription()
				);

				if (!hasText(description)) {
					throw new BusinessException(
							ErrorCode.BAD_REQUEST,
							"Extra charge description is required."
					);
				}

				if (submittedCharge.getAmount() == null
						|| submittedCharge.getAmount()
						.compareTo(BigDecimal.ZERO) <= 0) {

					throw new BusinessException(
							ErrorCode.BAD_REQUEST,
							"Extra charge amount must be greater than zero."
					);
				}

				ExtraCharge charge = new ExtraCharge();

				charge.setBookingEntry(entry);
				charge.setEstimateEntry(null);
				charge.setDescription(description);

				charge.setAmount(
						Money.INR(submittedCharge.getAmount())
				);

				charge.setImage(
						resolveCloseSlipImage(
								submittedCharge.getImageFile(),
								submittedCharge.getImage()
						)
				);

				entry.getCharges().add(charge);
			}
		}

		/*
		 * Both booking and entry are already managed because they were loaded
		 * inside this transaction. Do not call repository save/merge here.
		 */
		BookingUtil.calculateTotal(entry);

		boolean allCompleted = allDutiesCompleted(booking);

		applyBookingStatusAfterDutyClosure(booking, allCompleted);

		BookingUtil.calculateTotalAmount(booking);

		/*
		 * Flush the complete aggregate once.
		 *
		 * CascadeType.ALL persists new ExtraCharge entities through:
		 * Booking -> BookingEntry -> ExtraCharge.
		 */
		bookingRepository.flush();
		syncInvoiceIfGenerated(booking, orgId);

		DutyClosedEvent dutyClosedEvent =
				dutyEventAssembler.toDutyClosedEvent(
						booking,
						entry
				);

		publishEvent(dutyClosedEvent);

		if (booking.getStatus() == BookingStatus.COMPLETED
				&& previousBookingStatus != BookingStatus.COMPLETED) {
			eventPublisher.publishEvent(
					new BookingCompletedEvent(
							booking.getBookingId(),
							orgId
					)
			);
		}

		return bookingAssembler.assemble(booking);
	}

	@Transactional
	public BookingDTO reCloseDuty(CloseDutyCommand cmd, String orgId) {

		Booking booking = getBookingForUpdate(
				cmd.getBookingId(),
				orgId
		);


		BookingStatus previousBookingStatus =
				booking.getStatus();

		BookingEntry entry = getDutyFromBooking(
				booking,
				cmd.getDutyId()
		);

		ensureDutyStatus(
				entry,
				Set.of(DutyStatus.COMPLETED),
				"re-close duty"
		);

		List<ExtraCharge> previousCharges =
				entry.getCharges() == null
						? new ArrayList<>()
						: new ArrayList<>(entry.getCharges());

		if (entry.getCharges() == null) {
			entry.setCharges(new ArrayList<>());
		}

		List<CloseDutyCommand.ExtraCharge> submittedCharges =
				cmd.getExtraCharges() == null
						? List.of()
						: cmd.getExtraCharges();

		/*
		 * Validate all submitted charges before replacing files or mutating
		 * the persistent collection.
		 */
		for (CloseDutyCommand.ExtraCharge submittedCharge
				: submittedCharges) {

			if (submittedCharge == null) {
				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Extra charge details are required."
				);
			}

			String description = clean(
					submittedCharge.getDescription()
			);

			if (!hasText(description)) {
				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Extra charge description is required."
				);
			}

			if (submittedCharge.getAmount() == null
					|| submittedCharge.getAmount()
					.compareTo(BigDecimal.ZERO) <= 0) {

				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Extra charge amount must be greater than zero."
				);
			}
		}

		entry.setReportingTime(cmd.getReportingTime());
		entry.setStartingKM(cmd.getStartingKM());
		entry.setDropTime(cmd.getDropTime());
		entry.setClosingKM(cmd.getClosingKM());
		entry.setNightChargeble(cmd.getNightChargeble());
		entry.setStartAt(cmd.getStartAt());
		entry.setEndAt(cmd.getEndAt());

		entry.setDutySlipImage(
				resolveRecloseSlipImage(
						entry.getDutySlipImage(),
						cmd.getDutySlipImage(),
						cmd.getDutySlipImageFile()
				)
		);

		List<ExtraCharge> managedCharges =
				entry.getCharges();

		Set<String> retainedExtraChargeImages =
				new HashSet<>();

		for (int index = 0;
		     index < submittedCharges.size();
		     index++) {

			CloseDutyCommand.ExtraCharge submittedCharge =
					submittedCharges.get(index);

			ExtraCharge charge;

			if (index < managedCharges.size()) {
				charge = managedCharges.get(index);
			} else {
				charge = new ExtraCharge();
				charge.setBookingEntry(entry);
				charge.setEstimateEntry(null);

				managedCharges.add(charge);
			}

			String existingImage = charge.getImage();

			String resolvedImage =
					resolveRecloseSlipImage(
							existingImage,
							submittedCharge.getImage(),
							submittedCharge.getImageFile()
					);

			if (hasText(resolvedImage)) {
				retainedExtraChargeImages.add(
						clean(resolvedImage)
				);
			}

			charge.setBookingEntry(entry);
			charge.setEstimateEntry(null);

			charge.setDescription(
					clean(submittedCharge.getDescription())
			);

			charge.setAmount(
					Money.INR(submittedCharge.getAmount())
			);

			charge.setImage(resolvedImage);
		}

		/*
		 * Remove charges that were deleted from the re-close form.
		 *
		 * Removing them from the managed collection lets orphanRemoval delete
		 * them from the database during flush.
		 */
		while (managedCharges.size()
				> submittedCharges.size()) {

			managedCharges.remove(
					managedCharges.size() - 1
			);
		}

		BookingUtil.calculateTotal(entry);

		boolean allCompleted = allDutiesCompleted(booking);

		applyBookingStatusAfterDutyClosure(booking, allCompleted);

		BookingUtil.calculateTotalAmount(booking);

		/*
		 * Do not call repository save/merge for the already managed entities.
		 */
		bookingRepository.flush();
		syncInvoiceIfGenerated(booking, orgId);

		/*
		 * Delete receipt images belonging to charges removed from the form.
		 * This runs after a successful database flush.
		 */
		deleteRemovedExtraChargeImages(
				previousCharges,
				retainedExtraChargeImages
		);

		DutyReClosedEvent dutyReClosedEvent =
				dutyEventAssembler.toDutyReClosedEvent(
						booking,
						entry
				);

		publishEvent(dutyReClosedEvent);

		/*
		 * Publish booking completion only when this operation changes the
		 * booking from CONFIRMED/RUNNING to COMPLETED.
		 */
		if (booking.getStatus() == BookingStatus.COMPLETED
				&& previousBookingStatus != BookingStatus.COMPLETED) {

			eventPublisher.publishEvent(
					new BookingCompletedEvent(
							booking.getBookingId(),
							orgId
					)
			);
		}

		return bookingAssembler.assemble(booking);
	}

	/* =================== PAYMENTS ========================== */

	@Transactional
	public BookingDTO addPayment(BookingPaymentCommand cmd, String orgId) {
		Booking booking = getBookingForUpdate(cmd.bookingId(), orgId);

		Payment payment = new Payment();
		payment.setOrgId(orgId);
		payment.setBooking(booking);
		payment.setPaymentMode(cmd.paymentMode());
		payment.setReceivedAmount(cmd.receivedAmount());
		payment.setTds(cmd.tds());

		if (cmd.tds() == null) {
			payment.setTds(Money.INR(0f));
		}

		payment.setTransactionDate(cmd.transactionDate());
		payment.setTransactionNumber(cmd.transactionNumber());
		payment.setRemarks(cmd.remarks());
		payment.setGateway(PaymentGateway.MANUAL_ENTRY);
		payment.setStatus(PaymentStatus.PENDING);

		booking.getPayments().add(payment);

		Booking saved = bookingRepository.save(booking);
		syncInvoiceIfGenerated(saved, orgId);

		return bookingAssembler.assemble(saved);
	}

	@Transactional
	public BookingDTO updatePayment(BookingPaymentCommand cmd, String orgId) {
		if (cmd.paymentId() == null) {
			throw new BusinessException(ErrorCode.PAYMENT_ID_REQUIRED, "Payment ID is required for update.");
		}

		Booking booking = getBookingForUpdate(cmd.bookingId(), orgId);

		Payment payment = booking.getPayments().stream()
				.filter(p -> p.getId().equals(cmd.paymentId()))
				.findFirst()
				.orElseThrow(() -> new NotFoundException(
						ErrorCode.PAYMENT_NOT_FOUND,
						"Payment not found in the booking."
				));

		payment.setPaymentMode(cmd.paymentMode());
		payment.setReceivedAmount(cmd.receivedAmount());
		payment.setTds(cmd.tds());

		if (cmd.tds() == null) {
			payment.setTds(Money.INR(0f));
		}

		payment.setTransactionDate(cmd.transactionDate());
		payment.setTransactionNumber(cmd.transactionNumber());
		payment.setRemarks(cmd.remarks());
		payment.setGateway(PaymentGateway.MANUAL_ENTRY);
		payment.setStatus(PaymentStatus.PENDING);

		Booking saved = bookingRepository.save(booking);
		syncInvoiceIfGenerated(saved, orgId);

		return bookingAssembler.assemble(saved);
	}

	@Transactional
	@SuppressWarnings("null")
	public BookingDTO confirmPayment(String bookingId, String paymentId, String orgId) {
		Booking booking = getBookingForUpdate(bookingId, orgId);

		Payment payment = booking.getPayments().stream()
				.filter(p -> p.getId().equals(paymentId))
				.findFirst()
				.orElseThrow(() -> new NotFoundException(
						ErrorCode.PAYMENT_NOT_FOUND,
						"Payment not found in the booking."
				));

		if (payment.getStatus().equals(PaymentStatus.CONFIRMED)) {
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CONFIRMED, "Payment is already confirmed.");
		}

		payment.setStatus(PaymentStatus.CONFIRMED);

		Booking saved = bookingRepository.save(booking);
		syncInvoiceIfGenerated(saved, orgId);

		PaymentConfirmedEvent event = paymentEventAssembler.toPaymentConfirmedEvent(booking, payment);
		eventPublisher.publishEvent(event);

		return bookingAssembler.assemble(saved);
	}

	/* ============================= FETCH =============================== */

	@Transactional(readOnly = true)
	public BookingDTO getBookingDTO(String bookingId, String orgId) {
		return bookingAssembler.assemble(this.getBooking(bookingId, orgId));
	}

	@Transactional(readOnly = true)
	public Booking getBooking(String bookingId, String orgId) {
		return this.bookingRepository.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new NotFoundException(
						ErrorCode.BOOKING_NOT_FOUND,
						"Booking not found with this booking Id."
				));
	}

	@Transactional(readOnly = true)
	@SuppressWarnings("null")
	public PageResult<BookingListItem> getPage(String orgId, BookingPageRequest request) {
		String sortBy = resolveBookingSortBy(request.sortBy());
		Sort.Direction direction = request.direction();

		Page<Booking> bookings;

		if ("firstDutyReportingTime".equals(sortBy)) {
			Pageable pageable = PageRequest.of(request.page(), request.size());

			bookings = direction == Sort.Direction.ASC
					? bookingRepository.searchBookingsOrderByFirstDutyReportingTimeAsc(
					orgId,
					request.status(),
					request.searchstr(),
					pageable
			)
					: bookingRepository.searchBookingsOrderByFirstDutyReportingTimeDesc(
					orgId,
					request.status(),
					request.searchstr(),
					pageable
			);
		} else {
			Pageable pageable = PageRequest.of(request.page(), request.size(), Sort.by(direction, sortBy));

			bookings = bookingRepository.searchBookingsByStatus(
					orgId,
					request.status(),
					request.searchstr(),
					pageable
			);
		}

		return PageResult.from(bookingAssembler.assemble(bookings), sortBy, direction);
	}

	@Transactional(readOnly = true)
	@SuppressWarnings("null")
	public PageResult<DutyListItem> getDutyPage(String orgId, DutyPageRequest request) {
		String sortBy = resolveDutySortBy(request.sortBy());
		Sort.Direction direction = request.direction();

		Pageable pageable = PageRequest.of(request.page(), request.size(), Sort.by(direction, sortBy));

		Page<BookingEntry> duties = bookingEntryRepository.searchDuties(
				orgId,
				request.status(),
				request.bookingStatus(),
				request.bookingId(),
				request.searchstr(),
				pageable
		);

		return PageResult.from(bookingAssembler.assembleDutyPage(duties), sortBy, direction);
	}

	@Transactional(readOnly = true)
	public List<Booking> getClientBookings(String clientId, String orgId) {
		return this.bookingRepository.findByClientIdAndOrgId(clientId, orgId);
	}

	/* ================================= HELPERS ======================= */

	@Transactional(readOnly = true)
	@SuppressWarnings("null")
	Package getPackage(String packageId) {
		return this.packageRepository.findById(packageId).orElse(null);
	}

	@Transactional(readOnly = true)
	BookingEntry getDutyFromBooking(Booking booking, String dutyId) {
		return booking.getEntries().stream()
				.filter(e -> e.getDutyId().equals(dutyId))
				.findFirst()
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));
	}

	/*
	 * ApplicationEventPublisher.publishEvent(Object) lives in a @NonNullApi package, so
	 * Eclipse's null analysis flags every call site in the large closeDuty/reCloseDuty
	 * methods below as needing an unchecked conversion, even though the event is never
	 * null. Routed through this tiny helper so the suppression stays scoped instead of
	 * blanketing those large methods.
	 */
	@SuppressWarnings("null")
	private void publishEvent(Object event) {
		eventPublisher.publishEvent(event);
	}

	private BigDecimal normalizeDiscount(BigDecimal discountAmount) {
		if (discountAmount == null) {
			return BigDecimal.ZERO;
		}

		if (discountAmount.signum() < 0) {
			throw new IllegalArgumentException("Discount cannot be negative");
		}

		return discountAmount;
	}

	private String resolveCloseSlipImage(MultipartFile incomingFile, String incomingFilename) {
		if (incomingFile == null || incomingFile.isEmpty()) {
			return clean(incomingFilename);
		}

		return saveUploadedSlip(incomingFile);
	}

	private String resolveRecloseSlipImage(
			String existingFilename,
			String incomingFilename,
			MultipartFile incomingFile) {

		String existing = clean(existingFilename);
		String incoming = clean(incomingFilename);

		if (incomingFile == null || incomingFile.isEmpty()) {
			if (hasText(existing)) {
				return existing;
			}

			return incoming;
		}

		if (hasText(existing)) {
			deleteFileQuietly(existing);
		} else if (hasText(incoming)) {
			deleteFileQuietly(incoming);
		}

		return saveUploadedSlip(incomingFile);
	}

	private String saveUploadedSlip(MultipartFile file) {
		try {
			return fileService.saveFile(file);
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to save uploaded slip image", ex);
		}
	}

	private void deleteRemovedExtraChargeImages(
			List<ExtraCharge> existingCharges,
			Set<String> retainedImages) {

		if (existingCharges == null || existingCharges.isEmpty()) {
			return;
		}

		for (ExtraCharge oldCharge : existingCharges) {
			if (oldCharge == null || !hasText(oldCharge.getImage())) {
				continue;
			}

			String oldImage = clean(oldCharge.getImage());

			if (!retainedImages.contains(oldImage)) {
				deleteFileQuietly(oldImage);
			}
		}
	}

	private void deleteFileQuietly(String filename) {
		String cleanFilename = clean(filename);

		if (!hasText(cleanFilename)) {
			return;
		}

		try {
			fileService.deleteFile(cleanFilename);
		} catch (Exception ignored) {
			// File cleanup should not block duty reclose transaction.
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String clean(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}

	private void syncInvoiceIfGenerated(Booking booking, String orgId) {
		if (!hasGeneratedInvoice(booking)) {
			return;
		}

		eventPublisher.publishEvent(
				new SyncInvoiceEvent(
						booking.getBookingId(),
						booking.getInvoiceNumber(),
						orgId
				)
		);
	}

	private void reopenCompletedBookingAfterDutyAdded(Booking booking) {
		if (booking.getStatus() == BookingStatus.COMPLETED
				&& !hasGeneratedInvoice(booking)) {
			booking.setStatus(BookingStatus.CONFIRMED);
		}
	}

	private void markBookingRunningUnlessTerminal(Booking booking) {
		if (!preserveTerminalBookingStatus(booking)) {
			booking.setStatus(BookingStatus.RUNNING);
		}
	}

	private void applyBookingStatusAfterDutyClosure(Booking booking, boolean allCompleted) {
		if (preserveTerminalBookingStatus(booking)) {
			return;
		}

		booking.setStatus(allCompleted ? BookingStatus.COMPLETED : BookingStatus.RUNNING);
	}

	private boolean preserveTerminalBookingStatus(Booking booking) {
		return hasGeneratedInvoice(booking)
				|| booking.getStatus() == BookingStatus.BILLED
				|| booking.getStatus() == BookingStatus.CANCELLED;
	}

	private boolean hasGeneratedInvoice(Booking booking) {
		return booking != null && hasText(booking.getInvoiceNumber());
	}

	private String resolveBookingSortBy(String sortBy) {
		if (sortBy == null || sortBy.isBlank()) {
			return "createdAt";
		}

		return switch (sortBy.trim()) {
			case "createdAt", "creationDate", "CREATED_AT" -> "createdAt";
			case "updatedAt", "updationDate", "UPDATED_AT" -> "updatedAt";
			case "firstDutyReportingTime", "firstDutyReportingDate", "FIRST_DUTY_REPORTING_TIME" -> "firstDutyReportingTime";
			default -> "createdAt";
		};
	}

	private String resolveDutySortBy(String sortBy) {
		if (sortBy == null || sortBy.isBlank()) {
			return "reportingTime";
		}

		return switch (sortBy.trim()) {
			case "reportingTime", "reportingDate", "REPORTING_TIME" -> "reportingTime";
			case "createdAt", "creationDate", "CREATED_AT" -> "createdAt";
			default -> "reportingTime";
		};
	}

	private Booking getBookingForUpdate(String bookingId, String orgId) {
		return this.bookingRepository.lockByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new NotFoundException(
						ErrorCode.BOOKING_NOT_FOUND,
						"Booking not found with this booking Id."
				));
	}

	private void ensureBookingStatus(
			Booking booking,
			Set<BookingStatus> allowedStatuses,
			String action
	) {
		if (booking == null || booking.getStatus() == null || !allowedStatuses.contains(booking.getStatus())) {
			throw new BusinessException(
					ErrorCode.INVALID_BOOKING_STATUS,
					"Cannot " + action + " when booking status is " + statusName(booking == null ? null : booking.getStatus())
			);
		}
	}

	private void ensureDutyStatus(
			BookingEntry entry,
			Set<DutyStatus> allowedStatuses,
			String action
	) {
		if (entry == null || entry.getStatus() == null || !allowedStatuses.contains(entry.getStatus())) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"Cannot " + action + " when duty status is " + statusName(entry == null ? null : entry.getStatus())
			);
		}
	}

	private boolean allDutiesCompleted(Booking booking) {
		return booking.getEntries() != null
				&& !booking.getEntries().isEmpty()
				&& booking.getEntries()
				.stream()
				.allMatch(entry -> entry.getStatus() == DutyStatus.COMPLETED);
	}

	private String statusName(Enum<?> status) {
		return status == null ? "UNKNOWN" : status.name();
	}
}

package com.core.services;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.driver.DriverDTO;
import com.core.dtos.driver.DriverGarageOptionDTO;
import com.core.dtos.driver.DriverProfileUpdateRequest;
import com.core.dtos.driver.DriverRatingSummaryResponse;
import com.core.dtos.driverduty.DriverAppDutyTokenResponse;
import com.core.dtos.driverduty.DriverDutyAcceptanceResponse;
import com.core.dtos.driverduty.DriverDutyDeclineRequest;
import com.core.dtos.driverduty.DriverDutyDeclineResponse;
import com.core.dtos.driverduty.DriverDutyLinkResponse;
import com.core.dtos.driverduty.DutyRouteLegResponse;
import com.core.dtos.driverduty.DutySummaryForDriverDTO;
import com.core.dtos.client.app.NotificationSummaryResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.mapper.DriverAssembler;
import com.core.models.BookingEntry;
import com.core.models.Driver;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.NotificationRecipientType;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverRepository;
import com.core.services.config.CityGarageService;
import com.core.util.AddressUtil;

import lombok.RequiredArgsConstructor;

/*
 * Backs the authenticated driver-self-service dashboard (/driver/app/**): "my active
 * duties", "my history", "my duty detail", and minting a duty-execution token for a
 * duty the driver actually owns.
 *
 * Deliberately thin — duty EXECUTION (start/end, odometer, fare calc, payment QR) is
 * not reimplemented here at all. generateToken() below delegates to the existing,
 * unmodified ExternalDriverDutyService.generateDriverDutyLink so the Driver App drives
 * the exact same battle-tested /driver-api/duty/{token}/* endpoints an employee-sent
 * WhatsApp link would.
 */
@Service
@RequiredArgsConstructor
public class DriverAppService {

	private static final List<DutyStatus> ACTIVE_STATUSES = List.of(DutyStatus.ALLOTTED, DutyStatus.RUNNING);
	private static final List<BookingStatus> ACTIVE_BOOKING_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.RUNNING);

	private final BookingEntryRepository bookingEntryRepository;
	private final DriverRepository driverRepository;
	private final ExternalDriverDutyService externalDriverDutyService;
	private final DriverDutyCheckpointRepository checkpointRepository;
	private final NotificationService notificationService;
	private final DriverAssembler driverAssembler;
	private final DriverDocumentService driverDocumentService;
	private final DriverRatingService driverRatingService;
	private final CityGarageService cityGarageService;

	/*
	 * Registers this driver's push token for assignment-notification delivery.
	 * The recipient id always comes from the JWT-resolved driver record, never
	 * from client input -- there is no driverId parameter for a caller to
	 * supply. Reuses the existing generic DeviceToken upsert (same one the
	 * CLIENT notification path already uses), just under
	 * NotificationRecipientType.DRIVER.
	 */
	@Transactional
	public void registerDeviceToken(String orgId, String userId, String token, String platform) {
		Driver driver = resolveDriver(orgId, userId);
		notificationService.registerDeviceToken(orgId, NotificationRecipientType.DRIVER, driver.getId(), token, platform);
	}

	// Mirrors client.app.NotificationController's list()/markRead() -- same
	// generic NotificationService, just NotificationRecipientType.DRIVER and
	// a driver row (resolved from the JWT, never client-supplied) instead of
	// a client row.
	@Transactional(readOnly = true)
	public NotificationSummaryResponse getNotifications(String orgId, String userId) {
		Driver driver = resolveDriver(orgId, userId);
		return notificationService.list(orgId, NotificationRecipientType.DRIVER, driver.getId());
	}

	@Transactional
	public void markNotificationRead(String orgId, String userId, String notificationId) {
		Driver driver = resolveDriver(orgId, userId);
		notificationService.markRead(notificationId, orgId, NotificationRecipientType.DRIVER, driver.getId());
	}

	// Deliberately narrower than DriverController's employee-side PUT
	// /employee/drivers/{id} (DriverService#updateDriver): a driver may only
	// touch their own name/gender/alternatePhone/email/address here (see
	// DriverProfileUpdateRequest) -- clientId, ownership, phone, and the KYC
	// numbers stay ops/document-verification-controlled. The driver row is
	// always the JWT-resolved one, never a path/body-supplied id.
	@Transactional
	public DriverDTO updateOwnProfile(String orgId, String userId, DriverProfileUpdateRequest request) {
		Driver driver = resolveDriver(orgId, userId);

		if (request.name() != null) {
			Name name = driver.getName() != null ? driver.getName() : new Name();
			name.setSalutation(request.name().salutation());
			name.setFirstName(request.name().firstName());
			name.setLastName(request.name().lastName());
			driver.setName(name);
		}

		if (request.gender() != null) {
			driver.setGender(request.gender());
		}

		if (request.alternatePhone() != null) {
			driver.setAlternatePhone(request.alternatePhone());
		}

		if (request.email() != null) {
			driver.setEmail(request.email());
		}

		if (request.address() != null) {
			DisplayAddress address = driver.getAddress() != null ? driver.getAddress() : new DisplayAddress();
			address.setFormattedAddress(request.address().formattedAddress());
			address.setCity(request.address().city());
			address.setState(request.address().state());
			address.setPincode(request.address().pincode());
			address.setCountryCode(request.address().countryCode());
			driver.setAddress(address);
		}

		if (request.garageLocation() != null) {
			driver.setGarageLocation(AddressUtil.toAddressSnapshot(request.garageLocation()));
		}

		if (request.experienceYears() != null) {
			driver.setExperienceYears(request.experienceYears());
		}

		return driverAssembler.assemble(driverRepository.save(driver));
	}

	@Transactional(readOnly = true)
	public DriverDTO getOwnProfile(String orgId, String userId) {
		return driverAssembler.assemble(resolveDriver(orgId, userId));
	}

	// Real, org-configured garage options for the onboarding/profile garage
	// picker -- reuses CityGarageService (the same source ops's own garage
	// config screen manages under /config/city-garage) rather than a second,
	// driver-scoped copy of the data. No Authority check here (unlike the
	// ops-side GARAGE_VIEW-gated endpoint): any authenticated driver may read
	// their own org's garage list, same as getOwnProfile/getOwnRating above.
	@Transactional(readOnly = true)
	public List<DriverGarageOptionDTO> getGarages(String orgId) {
		return cityGarageService.getList(orgId).stream()
				.map(g -> new DriverGarageOptionDTO(g.getId(), g.getCity(), AddressUtil.toAddressSnapshotDTO(g.getGarageLocation())))
				.toList();
	}

	// Backs the Activity screen's rating display -- real, combining the
	// customer's TripRating aggregate with ops's own DriverOpsRating (see
	// DriverRatingService). Never the raw fare/earnings figures: those are
	// deliberately not exposed to the driver.
	@Transactional(readOnly = true)
	public DriverRatingSummaryResponse getOwnRating(String orgId, String userId) {
		Driver driver = resolveDriver(orgId, userId);
		return driverRatingService.getRatingSummary(orgId, driver.getId());
	}

	@Transactional(readOnly = true)
	public Page<DutySummaryForDriverDTO> getActiveDuties(String orgId, String userId, Pageable pageable) {
		Driver driver = resolveDriver(orgId, userId);
		return bookingEntryRepository
				.findActiveDutiesForDriver(orgId, driver.getId(), ACTIVE_STATUSES, ACTIVE_BOOKING_STATUSES, pageable)
				.map(this::toSummary);
	}

	@Transactional(readOnly = true)
	public Page<DutySummaryForDriverDTO> getDutyHistory(String orgId, String userId, Pageable pageable) {
		Driver driver = resolveDriver(orgId, userId);
		return bookingEntryRepository
				.findCompletedDutiesForDriver(orgId, driver.getId(), DutyStatus.COMPLETED, pageable)
				.map(this::toSummary);
	}

	@Transactional(readOnly = true)
	public DutySummaryForDriverDTO getDuty(String orgId, String userId, String dutyId) {
		Driver driver = resolveDriver(orgId, userId);
		return toSummary(findOwnedDuty(orgId, driver.getId(), dutyId));
	}

	// Real, on-demand pickup/drop/garage distance+ETA for a duty the driver
	// owns, computed by the same LocationService/provider chain as the
	// token-authenticated route endpoint -- see
	// ExternalDriverDutyService.getRouteForLeg(BookingEntry, String) for why
	// this overload (no token needed) exists.
	@Transactional(readOnly = true)
	public DutyRouteLegResponse getRouteForLeg(String orgId, String userId, String dutyId, String leg) {
		Driver driver = resolveDriver(orgId, userId);
		BookingEntry entry = findOwnedDuty(orgId, driver.getId(), dutyId);
		return externalDriverDutyService.getRouteForLeg(entry, leg);
	}

	@Transactional
	public DriverDutyAcceptanceResponse acceptDuty(String orgId, String userId, String dutyId) {
		Driver driver = resolveDriver(orgId, userId);
		BookingEntry unlocked = findOwnedDuty(orgId, driver.getId(), dutyId);
		BookingEntry entry = lockEntry(unlocked.getId());

		if (entry.getDriverAcceptedAt() != null) {
			// Idempotent -- a retried/duplicate accept tap is a no-op success, not an error.
			return new DriverDutyAcceptanceResponse(entry.getDutyId(), true, entry.getDriverAcceptedAt());
		}

		// Safety gate: a driver whose licence/Aadhaar aren't both ops-verified
		// yet can't accept a duty at all -- checked again, harder, at actual
		// duty start (ExternalDriverDutyService) as the real "car leaves
		// garage" backstop. This early check is purely a faster, clearer
		// failure for the driver -- not the only enforcement point.
		if (!driverDocumentService.areRequiredDocumentsVerified(orgId, driver.getId())) {
			throw new BusinessException(
					ErrorCode.DRIVER_DOCUMENTS_NOT_VERIFIED,
					"Your documents must be verified by our operations team before you can accept duties"
			);
		}

		if (entry.getStatus() != DutyStatus.ALLOTTED) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"This duty can no longer be accepted (status: " + entry.getStatus() + ")"
			);
		}

		Instant acceptedAt = Instant.now();
		entry.setDriverAcceptedAt(acceptedAt);
		entry.setDriverDeclinedAt(null);
		entry.setDriverDeclineReason(null);
		bookingEntryRepository.save(entry);

		return new DriverDutyAcceptanceResponse(entry.getDutyId(), true, acceptedAt);
	}

	@Transactional
	public DriverDutyDeclineResponse declineDuty(String orgId, String userId, String dutyId, DriverDutyDeclineRequest request) {
		Driver driver = resolveDriver(orgId, userId);
		BookingEntry unlocked = findOwnedDuty(orgId, driver.getId(), dutyId);
		BookingEntry entry = lockEntry(unlocked.getId());

		String reason = request == null || request.reason() == null ? null : request.reason().trim();
		if (reason == null || reason.isBlank()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "A reason is required to decline a duty");
		}

		if (entry.getDriverDeclinedAt() != null) {
			// Idempotent -- a retried/duplicate decline tap is a no-op success, not an error.
			return new DriverDutyDeclineResponse(entry.getDutyId(), true, entry.getDriverDeclinedAt(), entry.getDriverDeclineReason());
		}

		if (entry.getStatus() != DutyStatus.ALLOTTED) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"This duty can no longer be declined (status: " + entry.getStatus() + ")"
			);
		}

		Instant declinedAt = Instant.now();
		entry.setDriverDeclinedAt(declinedAt);
		entry.setDriverDeclineReason(reason);
		bookingEntryRepository.save(entry);

		return new DriverDutyDeclineResponse(entry.getDutyId(), true, declinedAt, reason);
	}

	@Transactional
	public DriverAppDutyTokenResponse issueExecutionToken(String orgId, String userId, String dutyId) {
		Driver driver = resolveDriver(orgId, userId);
		BookingEntry entry = findOwnedDuty(orgId, driver.getId(), dutyId);

		if (entry.getStatus() != DutyStatus.ALLOTTED && entry.getStatus() != DutyStatus.RUNNING) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"This duty is not available for execution right now (status: " + entry.getStatus() + ")"
			);
		}

		if (entry.getStatus() == DutyStatus.ALLOTTED && entry.getDriverAcceptedAt() == null) {
			throw new BusinessException(
					ErrorCode.ACCESS_DENIED,
					"Accept this duty before starting it"
			);
		}

		// A duty can be individually ALLOTTED/RUNNING while its parent booking has already
		// closed out (e.g. a duty added to an already-invoiced, COMPLETED booking -- see
		// BookingService#reopenCompletedBookingAfterDutyAdded). Catch that mismatch here with
		// the same friendly, typed error the active-duties list check uses, instead of letting
		// the driver reach the execute screen and hit ExternalDriverDutyService's raw
		// booking-status exception.
		if (!ACTIVE_BOOKING_STATUSES.contains(entry.getBooking().getStatus())) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"This duty is not available for execution right now (booking status: " + entry.getBooking().getStatus() + ")"
			);
		}

		DriverDutyLinkResponse link = externalDriverDutyService.generateDriverDutyLink(
				entry.getBooking().getBookingId(),
				entry.getDutyId(),
				orgId
		);

		// The raw token is guaranteed to be the URL's last path segment: it's generated as
		// URL-safe base64 (no '/'), and the link is built as `<publicUrl>/<rawToken>` in
		// ExternalDriverDutyService.generateDriverDutyLink. Reused as-is rather than
		// changing that shared response shape, which the employee WhatsApp-link flow
		// also depends on.
		String url = link.url();
		String token = url.substring(url.lastIndexOf('/') + 1);

		return new DriverAppDutyTokenResponse(token, link.expiresAt());
	}

	private Driver resolveDriver(String orgId, String userId) {
		return driverRepository.findByUserId(userId)
				.filter(d -> orgId.equals(d.getOrgId()))
				.orElseThrow(() -> new NotFoundException(ErrorCode.DRIVER_NOT_FOUND, "Driver record not found for this account"));
	}

	private BookingEntry findOwnedDuty(String orgId, String driverId, String dutyId) {
		return bookingEntryRepository.findForDriverSelf(orgId, driverId, dutyId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));
	}

	/*
	 * Ownership (org + driver) is already checked by findOwnedDuty's query above
	 * this is called after -- this only takes the PESSIMISTIC_WRITE lock by id,
	 * same pattern as ExternalDriverDutyService.lockEntryForDutyExecution, to
	 * protect accept/decline against a genuine double-tap race.
	 */
	private BookingEntry lockEntry(String entryId) {
		return bookingEntryRepository.lockById(entryId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));
	}

	private DutySummaryForDriverDTO toSummary(BookingEntry e) {
		return new DutySummaryForDriverDTO(
				e.getDutyId(),
				e.getBooking().getBookingId(),
				e.getStatus(),

				e.getBooking().getClient() != null ? e.getBooking().getClient().getName().getDisplayName() : null,
				e.getBooking().getClient() != null ? e.getBooking().getClient().getPhone() : null,
				e.getAllotedVehicle() != null && e.getAllotedVehicle().getMasterVehicle() != null
						? e.getAllotedVehicle().getMasterVehicle().getName()
						: null,
				e.getAllotedVehicle() != null ? e.getAllotedVehicle().getRegistrationNumber() : null,

				e.getReportingLocation() != null ? e.getReportingLocation().getFormattedAddress() : null,
				e.getDropLocation() != null ? e.getDropLocation().getFormattedAddress() : null,
				e.getReportingTime(),
				e.getDropTime(),

				e.getStartingKM(),
				e.getClosingKM(),
				e.getStartAt(),
				e.getEndAt(),

				e.getDutyTotal(),

				e.getDriverAcceptedAt(),
				e.getDriverDeclinedAt(),
				e.getPickupOtpVerifiedAt(),

				// Real checkpoint lookups, same source of truth
				// ExternalDriverDutyService.confirmGarageReturn/closeDutyFromDriverApp
				// write to -- lets the mobile app's crash-recovery logic (resumeDuty.ts)
				// resume into the correct post-completion screen instead of guessing
				// from local state alone.
				checkpointRepository
						.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(e.getId(), DriverDutyCheckpointType.GARAGE_RETURN)
						.map(c -> c.getSubmittedAt())
						.orElse(null),
				checkpointRepository
						.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(e.getId(), DriverDutyCheckpointType.CLOSE)
						.map(c -> c.getSubmittedAt())
						.orElse(null)
		);
	}
}

package com.core.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.DriverDutyEndRequest;
import com.core.dtos.driverduty.DriverDutyEndResponse;
import com.core.dtos.driverduty.DriverDutyExpenseInput;
import com.core.dtos.driverduty.DriverDutyLinkResponse;
import com.core.dtos.driverduty.DriverDutyStartRequest;
import com.core.dtos.driverduty.DriverDutyStartResponse;
import com.core.dtos.driverduty.DriverDutySummaryResponse;
import com.core.dtos.driverduty.DutyCompletionSummary;
import com.core.dtos.driverduty.PaymentInstruction;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.gateway.razerpay.QrPaymentStatusResponse;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.gateway.razerpay.RazorpayQrPayload;
import com.core.location.api.DistanceTimeResult;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutyCheckpoint;
import com.core.models.DriverDutyExpense;
import com.core.models.ExtraCharge;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DriverDutyCheckpointStatus;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DriverDutyExpenseStatus;
import com.core.models.enums.DriverDutyExpenseType;
import com.core.models.enums.DriverDutyTokenStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.BookingRepository;
import com.core.repositories.DriverDutyAccessTokenRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverDutyExpenseRepository;
import com.core.services.common.FileService;
import com.core.util.BookingUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExternalDriverDutyService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final BookingEntryRepository bookingEntryRepository;
	private final BookingRepository bookingRepository;
	private final DriverDutyAccessTokenRepository tokenRepository;
	private final DriverDutyCheckpointRepository checkpointRepository;
	private final DriverDutyExpenseRepository expenseRepository;
	private final FileService fileService;
	private final BookingService bookingService;
	private final RazorpayPaymentService razorpayPaymentService;

	/*
	 * Use LocationService here instead of directly injecting GeoProvider.
	 * LocationService delegates to your GeoProvider chain and avoids bean ambiguity
	 * when GoogleGeoProvider + FallbackGeoProvider both exist.
	 */
	private final LocationService locationService;

	private String driverDutyPublicUrl = "https://sandbox.fleetovo.com/extrenal";

	/*
	 * =========================================================
	 * EMPLOYEE SIDE: GENERATE LINK
	 * =========================================================
	 */

	@Transactional
	public DriverDutyLinkResponse generateDriverDutyLink(String bookingId, String dutyId, String orgId) {

		BookingEntry entry = bookingEntryRepository.findByDutyIdAndOrgId(dutyId, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found."));

		if (!entry.getBooking().getBookingId().equals(bookingId)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Duty does not belong to this booking");
		}

		Booking booking = entry.getBooking();

		if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.RUNNING) {
			throw new BusinessException(
					ErrorCode.INVALID_BOOKING_STATUS,
					"Driver duty link cannot be generated when booking status is " + booking.getStatus()
			);
		}

		if (entry.getStatus() != DutyStatus.ALLOTTED && entry.getStatus() != DutyStatus.RUNNING) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"Duty must be allotted before driver link can be generated"
			);
		}

		tokenRepository
				.findFirstByBookingEntry_IdAndStatusOrderByCreatedAtDesc(entry.getId(), DriverDutyTokenStatus.ACTIVE)
				.ifPresent(existing -> {
					existing.setStatus(DriverDutyTokenStatus.REVOKED);
					existing.setRevokedAt(Instant.now());
					tokenRepository.save(existing);
				});

		String rawToken = generateRawToken();

		DriverDutyAccessToken accessToken = new DriverDutyAccessToken();
		accessToken.setOrgId(orgId);
		accessToken.setDutyId(entry.getDutyId());
		accessToken.setDriverId(entry.getDriverId());
		accessToken.setBookingEntry(entry);
		accessToken.setTokenHash(hash(rawToken));
		accessToken.setStatus(DriverDutyTokenStatus.ACTIVE);
		accessToken.setExpiresAt(calculateDriverDutyLinkExpiry(entry));

		DriverDutyAccessToken saved = tokenRepository.save(accessToken);

		return new DriverDutyLinkResponse(
				entry.getDutyId(),
				entry.getBooking().getBookingId(),
				entry.getDriver() != null ? entry.getDriver().getName().getDisplayName() : null,
				entry.getAllotedVehicle() != null ? entry.getAllotedVehicle().getRegistrationNumber() : null,
				driverDutyPublicUrl + "/" + rawToken,
				saved.getExpiresAt()
		);
	}

	/*
	 * =========================================================
	 * PUBLIC SIDE: VALIDATE LINK
	 * =========================================================
	 */

	@Transactional
	public DriverDutySummaryResponse getDutySummary(String rawToken) {
		DriverDutyAccessToken accessToken = resolveValidToken(rawToken);
		BookingEntry entry = accessToken.getBookingEntry();

		boolean started = checkpointRepository.existsByBookingEntry_IdAndCheckpointType(
				entry.getId(),
				DriverDutyCheckpointType.START
		);

		boolean completed = checkpointRepository.existsByBookingEntry_IdAndCheckpointType(
				entry.getId(),
				DriverDutyCheckpointType.END
		);

		String action = completed ? "COMPLETED" : started ? "END_SUBMISSION" : "START_SUBMISSION";

		return new DriverDutySummaryResponse(
				entry.getBooking().getBookingId(),
				entry.getDutyId(),
				action,

				entry.getBooking().getClient() != null
						? entry.getBooking().getClient().getName().getDisplayName()
						: null,

				entry.getDriver() != null
						? entry.getDriver().getName().getDisplayName()
						: null,

				entry.getAllotedVehicle() != null && entry.getAllotedVehicle().getMasterVehicle() != null
						? entry.getAllotedVehicle().getMasterVehicle().getName()
						: null,

				entry.getAllotedVehicle() != null
						? entry.getAllotedVehicle().getRegistrationNumber()
						: null,

				entry.getReportingLocation() != null
						? entry.getReportingLocation().getFormattedAddress()
						: "Reporting location not provided",

				entry.getDropLocation() != null
						? entry.getDropLocation().getFormattedAddress()
						: "Drop location not provided",

				entry.getReportingTime(),
				entry.getDropTime(),
				entry.getStartingKM(),
				entry.getClosingKM(),
				entry.getStartAt(),
				entry.getEndAt(),
				started,
				completed
		);
	}

	/*
	 * =========================================================
	 * PUBLIC SIDE: START SUBMISSION
	 * =========================================================
	 */

	@Transactional
	public DriverDutyStartResponse submitStart(
			String rawToken,
			DriverDutyStartRequest request,
			MultipartFile odometerPhoto,
			String ipAddress,
			String userAgent
	) throws IOException {

		DriverDutyAccessToken accessToken = resolveValidToken(rawToken);

		BookingEntry entry = bookingEntryRepository
				.lockByDutyIdAndOrgId(accessToken.getDutyId(), accessToken.getOrgId())
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		Booking booking = entry.getBooking();

		if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.RUNNING) {
			throw new BusinessException(
					ErrorCode.INVALID_BOOKING_STATUS,
					"Duty cannot be started when booking status is " + booking.getStatus()
			);
		}

		if (entry.getStatus() == DutyStatus.COMPLETED) {
			throw new BusinessException(ErrorCode.DUTY_ALREADY_CLOSED, "Duty already completed");
		}

		if (entry.getStatus() != DutyStatus.ALLOTTED) {
			throw new BusinessException(
					ErrorCode.ACCESS_DENIED,
					"Duty start can be submitted only after allotment"
			);
		}

		boolean alreadyStarted = checkpointRepository.existsByBookingEntry_IdAndCheckpointType(
				entry.getId(),
				DriverDutyCheckpointType.START
		);

		if (alreadyStarted) {
			throw new BusinessException(ErrorCode.DUTY_ALREADY_RUNNING, "Start details already submitted");
		}

		validateKm(request.odometerKm(), "Start KM is required");

		AddressSnapshot startLocation = normalizeDriverLocation(request.location());

		String photoName = saveImage(odometerPhoto);
		Instant submittedAt = Instant.now();

		DriverDutyCheckpoint checkpoint = new DriverDutyCheckpoint();
		checkpoint.setOrgId(accessToken.getOrgId());
		checkpoint.setBookingId(entry.getBooking().getBookingId());
		checkpoint.setDutyId(entry.getDutyId());
		checkpoint.setDriverId(entry.getDriverId());
		checkpoint.setFleetVehicleId(entry.getFleetVehicleId());
		checkpoint.setBookingEntry(entry);
		checkpoint.setCheckpointType(DriverDutyCheckpointType.START);
		checkpoint.setStatus(locationPresent(startLocation)
				? DriverDutyCheckpointStatus.ACCEPTED
				: DriverDutyCheckpointStatus.NEEDS_REVIEW);
		checkpoint.setOdometerKm(request.odometerKm());
		checkpoint.setOdometerPhoto(photoName);
		checkpoint.setLocation(startLocation);
		checkpoint.setAccuracyMeters(request.accuracyMeters());
		checkpoint.setLocationCapturedAt(request.locationCapturedAt());
		checkpoint.setSubmittedAt(submittedAt);
		checkpoint.setNotes(request.notes());
		checkpoint.setIpAddress(ipAddress);
		checkpoint.setUserAgent(userAgent);

		checkpointRepository.save(checkpoint);

		entry.setStartingKM(request.odometerKm());
		entry.setStartAt(submittedAt);
		entry.setStatus(DutyStatus.RUNNING);

		if (!locationPresent(entry.getGarageLocation()) && locationPresent(startLocation)) {
			entry.setGarageLocation(startLocation);
		}

		booking.setStatus(BookingStatus.RUNNING);

		bookingEntryRepository.save(entry);
		bookingRepository.save(booking);

		accessToken.setLastUsedAt(submittedAt);
		tokenRepository.save(accessToken);

		return new DriverDutyStartResponse(
				true,
				entry.getStatus().name(),
				entry.getStartingKM(),
				entry.getStartAt(),
				"Duty started successfully"
		);
	}

	/*
	 * =========================================================
	 * PUBLIC SIDE: END / DROP SUBMISSION
	 * =========================================================
	 */

	@Transactional
	public DriverDutyEndResponse submitEnd(
			String rawToken,
			DriverDutyEndRequest request,
			MultipartFile odometerPhoto,
			List<MultipartFile> receiptPhotos,
			String ipAddress,
			String userAgent
	) throws IOException {

		DriverDutyAccessToken accessToken = resolveValidToken(rawToken);

		BookingEntry entry = bookingEntryRepository
				.lockByDutyIdAndOrgId(accessToken.getDutyId(), accessToken.getOrgId())
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		Booking booking = entry.getBooking();

		if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.RUNNING) {
			throw new BusinessException(
					ErrorCode.INVALID_BOOKING_STATUS,
					"Duty cannot be ended when booking status is " + booking.getStatus()
			);
		}

		if (entry.getStatus() == DutyStatus.COMPLETED) {
			throw new BusinessException(ErrorCode.DUTY_ALREADY_CLOSED, "Duty already completed");
		}

		if (entry.getStatus() != DutyStatus.RUNNING) {
			throw new BusinessException(
					ErrorCode.DUTY_NOT_RUNNING,
					"Start details must be submitted before drop details"
			);
		}

		validateKm(request.odometerKm(), "End KM is required");

		if (entry.getStartingKM() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Start KM missing");
		}

		if (request.odometerKm() < entry.getStartingKM()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "End KM cannot be less than start KM");
		}

		boolean alreadyEnded = checkpointRepository.existsByBookingEntry_IdAndCheckpointType(
				entry.getId(),
				DriverDutyCheckpointType.END
		);

		if (alreadyEnded) {
			throw new BusinessException(ErrorCode.DUTY_NOT_RUNNING, "Drop details already submitted");
		}

		AddressSnapshot dropLocation = normalizeDriverLocation(request.location());

		String photoName = saveImage(odometerPhoto);
		Instant actualDropSubmittedAt = Instant.now();

		DriverDutyCheckpoint checkpoint = new DriverDutyCheckpoint();
		checkpoint.setOrgId(accessToken.getOrgId());
		checkpoint.setBookingId(entry.getBooking().getBookingId());
		checkpoint.setDutyId(entry.getDutyId());
		checkpoint.setDriverId(entry.getDriverId());
		checkpoint.setFleetVehicleId(entry.getFleetVehicleId());
		checkpoint.setBookingEntry(entry);
		checkpoint.setCheckpointType(DriverDutyCheckpointType.END);
		checkpoint.setStatus(locationPresent(dropLocation)
				? DriverDutyCheckpointStatus.ACCEPTED
				: DriverDutyCheckpointStatus.NEEDS_REVIEW);
		checkpoint.setOdometerKm(request.odometerKm());
		checkpoint.setOdometerPhoto(photoName);
		checkpoint.setLocation(dropLocation);
		checkpoint.setAccuracyMeters(request.accuracyMeters());
		checkpoint.setLocationCapturedAt(request.locationCapturedAt());
		checkpoint.setSubmittedAt(actualDropSubmittedAt);
		checkpoint.setNotes(request.notes());
		checkpoint.setIpAddress(ipAddress);
		checkpoint.setUserAgent(userAgent);

		DriverDutyCheckpoint savedCheckpoint = checkpointRepository.save(checkpoint);

		GarageReturnEstimate garageReturn = calculateGarageReturnEstimate(entry, dropLocation);

		int billableClosingKm = request.odometerKm() + garageReturn.distanceKmRoundedUp();
		Instant billableEndAt = actualDropSubmittedAt.plusSeconds(garageReturn.durationSeconds());

		entry.setClosingKM(billableClosingKm);
		entry.setDropTime(actualDropSubmittedAt);
		entry.setEndAt(billableEndAt);
		entry.setStatus(DutyStatus.COMPLETED);

		if (entry.getCharges() == null) {
			entry.setCharges(new ArrayList<>());
		}

		BigDecimal extraTotal = persistDriverExpensesAndBillingCharges(
				entry,
				savedCheckpoint,
				request.extraCharges(),
				receiptPhotos,
				accessToken.getOrgId()
		);

		BookingUtil.calculateTotal(entry);

		boolean anyOpenDuty = booking.getEntries() != null
				&& booking.getEntries()
				.stream()
				.anyMatch(duty -> duty.getStatus() != DutyStatus.COMPLETED);

		if (anyOpenDuty) {
			booking.setStatus(BookingStatus.RUNNING);
		}

		bookingEntryRepository.save(entry);
		Booking savedBooking = bookingRepository.save(BookingUtil.calculateTotalAmount(booking));

		if (!anyOpenDuty) {
			bookingService.completeBooking(savedBooking.getBookingId(), accessToken.getOrgId());

			savedBooking = bookingRepository
					.findByBookingIdAndOrgId(savedBooking.getBookingId(), accessToken.getOrgId())
					.orElse(savedBooking);
		}

		accessToken.setLastUsedAt(actualDropSubmittedAt);
		accessToken.setStatus(DriverDutyTokenStatus.COMPLETED);
		tokenRepository.save(accessToken);

		BigDecimal amountToCollect = calculatePendingAmount(savedBooking);

		PaymentInstruction instruction;

		if (amountToCollect.compareTo(BigDecimal.ZERO) > 0) {
			try {
				RazorpayQrPayload qrPayload = razorpayPaymentService.generateQR(
						accessToken.getOrgId(),
						savedBooking.getBookingId(),
						entry.getDutyId(),
						amountToCollect
				);

				String qrDisplayValue = firstNonBlank(
						qrPayload.getQrImageContent(),
						qrPayload.getQrImageUrl()
				);

				instruction = new PaymentInstruction(
						true,
						amountToCollect,
						qrDisplayValue,
						"RAZORPAY_UPI_QR_IMAGE",
						"Collect payment from client"
				);

			} catch (Exception ex) {
				ex.printStackTrace();

				instruction = new PaymentInstruction(
						true,
						amountToCollect,
						null,
						"QR_GENERATION_FAILED",
						"Payment is pending, but QR could not be generated"
				);
			}
		} else {
			instruction = new PaymentInstruction(
					false,
					amountToCollect,
					null,
					"NO_PAYMENT_REQUIRED",
					"No payment collection required"
			);
		}

		DutyCompletionSummary summary = new DutyCompletionSummary(
				savedBooking.getBookingId(),
				entry.getDutyId(),
				entry.getStartingKM(),
				entry.getClosingKM(),
				entry.getClosingKM() - entry.getStartingKM(),
				entry.getStartAt(),
				entry.getEndAt(),
				extraTotal,
				savedBooking.getTotal().getAmount(),
				amountToCollect
		);

		return new DriverDutyEndResponse(
				true,
				entry.getStatus().name(),
				summary,
				instruction,
				"Duty completed successfully"
		);
	}

	/*
	 * =========================================================
	 * PAYMENT STATUS
	 * =========================================================
	 */

	@Transactional
	public QrPaymentStatusResponse checkQrPaymentStatus(String rawToken) {

		DriverDutyAccessToken accessToken = resolveTokenForPaymentStatus(rawToken);
		BookingEntry entry = accessToken.getBookingEntry();

		try {
			return razorpayPaymentService.isPaidByQR(
					accessToken.getOrgId(),
					entry.getBooking().getBookingId(),
					entry.getDutyId()
			);
		} catch (Exception ex) {
			ex.printStackTrace();

			return QrPaymentStatusResponse.builder()
					.status("PENDING")
					.paid(false)
					.amount(BigDecimal.ZERO)
					.message("Unable to verify payment right now")
					.build();
		}
	}

	/*
	 * =========================================================
	 * HELPERS
	 * =========================================================
	 */

	private DriverDutyAccessToken resolveValidToken(String rawToken) {
		String tokenHash = hash(rawToken);

		DriverDutyAccessToken token = tokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Invalid duty link"));

		if (token.getStatus() == DriverDutyTokenStatus.REVOKED) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has been revoked");
		}

		if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
			token.setStatus(DriverDutyTokenStatus.EXPIRED);
			tokenRepository.save(token);

			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has expired");
		}

		if (token.getStatus() == DriverDutyTokenStatus.EXPIRED) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has expired");
		}

		return token;
	}

	private DriverDutyAccessToken resolveTokenForPaymentStatus(String rawToken) {
		String tokenHash = hash(rawToken);

		DriverDutyAccessToken token = tokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Invalid duty link"));

		if (token.getStatus() == DriverDutyTokenStatus.REVOKED) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has been revoked");
		}

		/*
		 * Payment polling must remain available after duty completion.
		 * Otherwise, frontend refresh cannot restore pending QR state.
		 */
		if (token.getStatus() == DriverDutyTokenStatus.COMPLETED) {
			return token;
		}

		if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
			token.setStatus(DriverDutyTokenStatus.EXPIRED);
			tokenRepository.save(token);

			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has expired");
		}

		if (token.getStatus() == DriverDutyTokenStatus.EXPIRED) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has expired");
		}

		return token;
	}

	private BigDecimal persistDriverExpensesAndBillingCharges(
			BookingEntry entry,
			DriverDutyCheckpoint checkpoint,
			List<DriverDutyExpenseInput> inputs,
			List<MultipartFile> receiptPhotos,
			String orgId
	) throws IOException {

		if (inputs == null || inputs.isEmpty()) {
			return BigDecimal.ZERO;
		}

		BigDecimal total = BigDecimal.ZERO;

		for (int i = 0; i < inputs.size(); i++) {
			DriverDutyExpenseInput input = inputs.get(i);

			if (input.amount() == null || input.amount().compareTo(BigDecimal.ZERO) <= 0) {
				continue;
			}

			MultipartFile receipt = receiptPhotos != null && receiptPhotos.size() > i
					? receiptPhotos.get(i)
					: null;

			String receiptName = receipt != null && !receipt.isEmpty()
					? saveImage(receipt)
					: null;

			DriverDutyExpense expense = new DriverDutyExpense();
			expense.setOrgId(orgId);
			expense.setDutyId(entry.getDutyId());
			expense.setBookingEntry(entry);
			expense.setCheckpoint(checkpoint);
			expense.setExpenseType(input.type() != null ? input.type() : DriverDutyExpenseType.OTHER);
			expense.setAmount(Money.INR(input.amount()));
			expense.setDescription(input.description());
			expense.setReceiptPhoto(receiptName);
			expense.setStatus(DriverDutyExpenseStatus.DRIVER_SUBMITTED);

			expenseRepository.save(expense);

			ExtraCharge billingCharge = new ExtraCharge();
			billingCharge.setBookingEntry(entry);
			billingCharge.setDescription(buildBillingChargeDescription(input));
			billingCharge.setAmount(Money.INR(input.amount()));

			entry.getCharges().add(billingCharge);

			total = total.add(input.amount());
		}

		return total;
	}

	private String buildBillingChargeDescription(DriverDutyExpenseInput input) {
		String type = input.type() != null ? input.type().name() : DriverDutyExpenseType.OTHER.name();

		if (input.description() == null || input.description().isBlank()) {
			return type;
		}

		return type + " - " + input.description();
	}

	private BigDecimal calculatePendingAmount(Booking booking) {
		if (booking.getTotal() == null || booking.getTotal().getAmount() == null) {
			return BigDecimal.ZERO;
		}

		BigDecimal paid = booking.getPayments() == null
				? BigDecimal.ZERO
				: booking.getPayments()
				.stream()
				.filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
				.map(p -> p.getReceivedAmount().getAmount())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal pending = booking.getTotal().getAmount().subtract(paid);

		return pending.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : pending;
	}

	private String saveImage(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Odometer photo is required");
		}

		String contentType = file.getContentType();

		if (contentType == null || !contentType.startsWith("image/")) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Only image files are allowed");
		}

		long maxSize = 10L * 1024L * 1024L;

		if (file.getSize() > maxSize) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Image size cannot exceed 10 MB");
		}

		return fileService.saveFile(file);
	}

	private void validateKm(Integer km, String message) {
		if (km == null || km < 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, message);
		}
	}

	private Instant calculateDriverDutyLinkExpiry(BookingEntry entry) {
		Instant now = Instant.now();

		if (entry.getDropTime() != null) {
			return entry.getDropTime().plus(Duration.ofHours(24));
		}

		if (entry.getReportingTime() != null) {
			Instant reportingTime = entry.getReportingTime();

			return reportingTime.isBefore(now)
					? now.plus(Duration.ofHours(48))
					: reportingTime.plus(Duration.ofHours(48));
		}

		return now.plus(Duration.ofHours(48));
	}

	private AddressSnapshot normalizeDriverLocation(AddressSnapshot location) {
		if (location == null) {
			return null;
		}

		String formattedAddress = location.getFormattedAddress();

		if (formattedAddress == null || formattedAddress.isBlank()) {
			formattedAddress = "Driver captured GPS location";
		}

		return new AddressSnapshot(
				formattedAddress,
				location.getGooglePlaceId(),
				location.getLatitude(),
				location.getLongitude()
		);
	}

	private boolean locationPresent(AddressSnapshot location) {
		return location != null && location.isvalid();
	}

	private GarageReturnEstimate calculateGarageReturnEstimate(
			BookingEntry entry,
			AddressSnapshot dropLocation
	) {
		AddressSnapshot garageLocation = resolveGarageLocation(entry);

		if (!locationPresent(dropLocation) || !locationPresent(garageLocation)) {
			return GarageReturnEstimate.zero();
		}

		try {
			DistanceTimeResult result = locationService.calculateDistanceAndTime(dropLocation, garageLocation);

			int distanceKmRoundedUp = BigDecimal.valueOf(result.distanceKm())
					.setScale(0, RoundingMode.CEILING)
					.intValue();

			return new GarageReturnEstimate(
					distanceKmRoundedUp,
					result.durationSeconds()
			);
		} catch (Exception ex) {
			/*
			 * Do not fail duty completion only because geo calculation failed.
			 * Odometer/photo/location proof is still captured.
			 *
			 * The duty will be calculated without return-to-garage estimate.
			 * Operations can manually review if needed.
			 */
			return GarageReturnEstimate.zero();
		}
	}

	private AddressSnapshot resolveGarageLocation(BookingEntry entry) {
		if (locationPresent(entry.getGarageLocation())) {
			return entry.getGarageLocation();
		}

		/*
		 * Fallback:
		 * Driver's START checkpoint is captured when leaving garage.
		 * If duty.garageLocation was not stored, use START checkpoint location.
		 */
		return checkpointRepository
				.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(
						entry.getId(),
						DriverDutyCheckpointType.START
				)
				.map(DriverDutyCheckpoint::getLocation)
				.filter(this::locationPresent)
				.orElse(null);
	}

	private String firstNonBlank(String primary, String fallback) {
		if (primary != null && !primary.isBlank()) {
			return primary;
		}

		if (fallback != null && !fallback.isBlank()) {
			return fallback;
		}

		return null;
	}

	private String generateRawToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);

		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(bytes);
	}

	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes()));
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to hash token", ex);
		}
	}

	private record GarageReturnEstimate(
			int distanceKmRoundedUp,
			long durationSeconds
	) {
		static GarageReturnEstimate zero() {
			return new GarageReturnEstimate(0, 0);
		}
	}
}
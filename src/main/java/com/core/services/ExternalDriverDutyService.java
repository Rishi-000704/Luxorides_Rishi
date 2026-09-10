package com.core.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.CashPaymentConfirmationResponse;
import com.core.dtos.driverduty.CloseDutyConfirmationResponse;
import com.core.dtos.driverduty.DriverDutyEndRequest;
import com.core.dtos.driverduty.DriverDutyEndResponse;
import com.core.dtos.driverduty.DriverDutyExpenseInput;
import com.core.dtos.driverduty.DriverDutyLinkResponse;
import com.core.dtos.driverduty.DriverDutyLocationPingRequest;
import com.core.dtos.driverduty.DriverDutyLocationResponse;
import com.core.dtos.driverduty.DriverDutyReturnGarageRequest;
import com.core.dtos.driverduty.DriverDutyStartRequest;
import com.core.dtos.driverduty.DriverDutyStartResponse;
import com.core.dtos.driverduty.DriverDutySummaryResponse;
import com.core.dtos.driverduty.DutyRouteLegResponse;
import com.core.dtos.driverduty.DutyCompletionSummary;
import com.core.dtos.driverduty.GarageReturnConfirmationResponse;
import com.core.dtos.driverduty.PackageFareBreakdown;
import com.core.dtos.driverduty.PaymentInstruction;
import com.core.dtos.driverduty.PickupOtpGenerateResponse;
import com.core.dtos.driverduty.PickupOtpVerifyRequest;
import com.core.dtos.driverduty.PickupOtpVerifyResponse;
import com.core.dtos.driverduty.ReturnRouteEstimate;
import com.core.events.DutyStartedEvent;
import com.core.events.PaymentConfirmedEvent;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.QrPaymentStatusResponse;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.gateway.razerpay.RazorpayQrPayload;
import com.core.location.api.DistanceTimeResult;
import com.core.location.api.GeoPoint;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutyCheckpoint;
import com.core.models.DriverDutyExpense;
import com.core.models.DriverDutyLiveLocation;
import com.core.models.ExtraCharge;
import com.core.models.Payment;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DriverDutyCheckpointStatus;
import com.core.models.enums.DriverDutyCheckpointType;
import com.core.models.enums.DriverDutyExpenseStatus;
import com.core.models.enums.DriverDutyExpenseType;
import com.core.models.enums.DriverDutyTokenStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentMode;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.BookingRepository;
import com.core.repositories.DriverDutyAccessTokenRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverDutyExpenseRepository;
import com.core.repositories.DriverDutyLiveLocationRepository;
import com.core.repositories.PaymentRepository;
import com.core.services.common.FileService;
import com.core.services.common.SMSService;
import com.core.util.BookingUtil;
import com.core.util.PackageFareBreakdownFactory;
import com.core.util.EtaEstimator;
import com.core.ws.DutyLocationChannelRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
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
	private final MockPaymentService mockPaymentService;
	private final PaymentGatewayConfigService paymentGatewayConfigService;
	private final DriverDutyTokenValidator tokenValidator;
	private final ApplicationEventPublisher eventPublisher;
	private final DriverDutyLiveLocationRepository liveLocationRepository;
	private final DutyLocationChannelRegistry dutyLocationChannelRegistry;
	private final FraudSignalService fraudSignalService;
	private final PasswordEncoder passwordEncoder;
	private final SMSService smsService;

	/*
	 * Use LocationService here instead of directly injecting GeoProvider.
	 * LocationService delegates to your GeoProvider chain and avoids bean ambiguity
	 * when GoogleGeoProvider + FallbackGeoProvider both exist.
	 */
	private final LocationService locationService;

	/*
	 * Self-reference to reach completeDutyEntryAndFinalizeBooking through the
	 * Spring proxy instead of a plain same-class method call -- a direct
	 * `this.completeDutyEntryAndFinalizeBooking(...)` call bypasses Spring AOP
	 * entirely, so its @Transactional would silently do nothing. ObjectProvider
	 * (rather than injecting this type directly) defers the lookup until
	 * .getObject() is actually called, which sidesteps the circular-dependency
	 * a bean depending on its own bean definition would otherwise hit at
	 * construction time.
	 */
	private final ObjectProvider<ExternalDriverDutyService> self;

	/*
	 * Appended after `self` deliberately -- Lombok's @RequiredArgsConstructor
	 * generates the constructor in field-declaration order, so adding these
	 * at the END keeps every existing positional constructor call (see
	 * ExternalDriverDutyServicePhase1Test / ExternalDriverDutyServiceRouteTest)
	 * a pure append, not a reordering.
	 */
	private final PaymentRepository paymentRepo;
	private final PaymentEventAssembler paymentEventAssembler;

	// Required, no default -- see driver.duty.public-url in application.properties.
	// A deployment that forgets to configure this fails to start rather than
	// silently sending drivers a sandbox link in production.
	@Value("${driver.duty.public-url}")
	private String driverDutyPublicUrl;

	private static final String DRIVER_DUTY_CASH_CONTEXT = "DRIVER_DUTY_CASH";

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
		accessToken.setTokenHash(tokenValidator.hash(rawToken));
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
		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);
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

				entry.getBooking().getClient() != null
						? entry.getBooking().getClient().getPhone()
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

	private static final Set<String> VALID_ROUTE_LEGS = Set.of("PICKUP", "DROP", "GARAGE");

	/*
	 * Real, on-demand route for one leg of the garage-to-garage model,
	 * computed the same way as the existing C->A return-leg estimate (same
	 * LocationService/provider chain) -- from each leg's known waypoints
	 * (garage/pickup/drop as captured on the booking), not the driver's live
	 * position. Read-only: uses accessToken.getBookingEntry() directly
	 * rather than lockEntryForDutyExecution, since this may be polled
	 * repeatedly during navigation and has nothing to write.
	 */
	@Transactional(readOnly = true)
	public DutyRouteLegResponse getRouteForLeg(String rawToken, String legParam) {
		String leg = legParam == null ? "" : legParam.toUpperCase(Locale.ROOT);
		if (!VALID_ROUTE_LEGS.contains(leg)) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "leg must be one of PICKUP, DROP, GARAGE");
		}

		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);
		BookingEntry entry = accessToken.getBookingEntry();

		AddressSnapshot from;
		AddressSnapshot to;
		switch (leg) {
			case "PICKUP" -> {
				from = resolveGarageLocation(entry);
				to = entry.getReportingLocation();
			}
			case "DROP" -> {
				from = entry.getReportingLocation();
				to = entry.getDropLocation();
			}
			default -> {
				from = entry.getDropLocation();
				to = resolveGarageLocation(entry);
			}
		}

		if (!locationPresent(from) || !locationPresent(to)) {
			return new DutyRouteLegResponse(leg, false, null, null, null, false, toGeoPoint(from), toGeoPoint(to), null);
		}

		try {
			DistanceTimeResult result = locationService.calculateDistanceAndTime(from, to);
			boolean routeAvailable = result.routeGeometry() != null && !result.routeGeometry().isEmpty();

			return new DutyRouteLegResponse(
					leg, true, result.distanceKm(), result.durationSeconds(), result.provider(),
					routeAvailable, toGeoPoint(from), toGeoPoint(to), result.routeGeometry()
			);
		} catch (Exception ex) {
			// Same non-fatal convention as calculateGarageReturnEstimate -- an
			// honest "unavailable" beats fabricating a route.
			return new DutyRouteLegResponse(leg, false, null, null, null, false, toGeoPoint(from), toGeoPoint(to), null);
		}
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

		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);

		BookingEntry entry = lockEntryForDutyExecution(accessToken.getDutyId(), accessToken.getOrgId());

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

		eventPublisher.publishEvent(
				new DutyStartedEvent(booking.getBookingId(), entry.getDutyId(), accessToken.getOrgId()));

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
	 * PUBLIC SIDE: PICKUP OTP
	 * =========================================================
	 * Real, server-verified pickup verification. The OTP is generated here,
	 * SMS'd to the customer on the booking (never the driver), hashed with
	 * the same PasswordEncoder used for login OTPs, and verified server-side
	 * only -- the mobile app never has a client-side success condition for
	 * this step. Reuses SMSService.sendOtp (same provider config/template as
	 * login OTP) rather than inventing a second SMS pathway.
	 */

	private static final int PICKUP_OTP_TTL_MINUTES = 10;
	private static final int PICKUP_OTP_MAX_ATTEMPTS = 5;

	@Transactional
	public PickupOtpGenerateResponse generatePickupOtp(String rawToken) {
		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);
		BookingEntry entry = lockEntryForDutyExecution(accessToken.getDutyId(), accessToken.getOrgId());

		if (entry.getStatus() != DutyStatus.RUNNING) {
			throw new BusinessException(
					ErrorCode.DUTY_NOT_RUNNING,
					"Duty must be started before generating a pickup OTP"
			);
		}

		if (entry.getPickupOtpVerifiedAt() != null) {
			// Idempotent -- pickup was already verified, nothing left to (re)send.
			return new PickupOtpGenerateResponse(true, 0, true);
		}

		String customerPhone = entry.getBooking().getClient() != null
				? entry.getBooking().getClient().getPhone()
				: null;

		if (customerPhone == null || customerPhone.isBlank()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Customer phone number is not available for this booking");
		}

		String otp = generate6DigitOtp();
		entry.setPickupOtpHash(passwordEncoder.encode(otp));
		entry.setPickupOtpExpiresAt(Instant.now().plus(Duration.ofMinutes(PICKUP_OTP_TTL_MINUTES)));
		entry.setPickupOtpAttempts(0);
		bookingEntryRepository.save(entry);

		boolean sent = smsService.sendOtp(accessToken.getOrgId(), customerPhone, otp, String.valueOf(PICKUP_OTP_TTL_MINUTES));

		if (!sent) {
			// Don't strand the driver on a code that was never actually delivered.
			entry.setPickupOtpHash(null);
			entry.setPickupOtpExpiresAt(null);
			entry.setPickupOtpAttempts(null);
			bookingEntryRepository.save(entry);

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Unable to send pickup OTP. SMS provider is not configured for this organization."
			);
		}

		return new PickupOtpGenerateResponse(true, PICKUP_OTP_TTL_MINUTES * 60, false);
	}

	@Transactional
	public PickupOtpVerifyResponse verifyPickupOtp(String rawToken, PickupOtpVerifyRequest request) {
		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);
		BookingEntry entry = lockEntryForDutyExecution(accessToken.getDutyId(), accessToken.getOrgId());

		if (entry.getPickupOtpVerifiedAt() != null) {
			// Idempotent -- a retried/duplicate verify tap is a no-op success.
			return new PickupOtpVerifyResponse(true, entry.getPickupOtpVerifiedAt());
		}

		if (entry.getPickupOtpHash() == null || entry.getPickupOtpExpiresAt() == null) {
			throw new BusinessException(ErrorCode.OTP_NOT_INITIATED, "No pickup OTP has been generated for this duty yet");
		}

		if (Instant.now().isAfter(entry.getPickupOtpExpiresAt())) {
			throw new BusinessException(ErrorCode.OTP_EXPIRED, "Pickup OTP has expired. Ask the client to resend it.");
		}

		String submitted = request == null || request.otp() == null ? "" : request.otp().trim();

		if (submitted.isBlank() || !passwordEncoder.matches(submitted, entry.getPickupOtpHash())) {
			int attempts = (entry.getPickupOtpAttempts() == null ? 0 : entry.getPickupOtpAttempts()) + 1;
			entry.setPickupOtpAttempts(attempts);

			if (attempts >= PICKUP_OTP_MAX_ATTEMPTS) {
				entry.setPickupOtpHash(null);
				entry.setPickupOtpExpiresAt(null);
				bookingEntryRepository.save(entry);

				throw new BusinessException(
						ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
						"Too many incorrect attempts. Ask the client to resend the OTP."
				);
			}

			bookingEntryRepository.save(entry);
			throw new BusinessException(ErrorCode.OTP_INVALID, "Incorrect code. Ask the client to confirm the OTP.");
		}

		Instant verifiedAt = Instant.now();
		entry.setPickupOtpVerifiedAt(verifiedAt);
		bookingEntryRepository.save(entry);

		return new PickupOtpVerifyResponse(true, verifiedAt);
	}

	/*
	 * =========================================================
	 * PUBLIC SIDE: END / DROP SUBMISSION
	 * =========================================================
	 */

	public DriverDutyEndResponse submitEnd(
			String rawToken,
			DriverDutyEndRequest request,
			MultipartFile odometerPhoto,
			List<MultipartFile> receiptPhotos,
			String ipAddress,
			String userAgent
	) throws IOException {

		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);

		AddressSnapshot dropLocation = normalizeDriverLocation(request.location());
		String photoName = saveImage(odometerPhoto);

		/*
		 * Everything that touches the duty entry or booking row happens inside
		 * this one call, as one atomic transaction -- see its own comment for
		 * why it can't be submitEnd's own (still-open, spanning the QR call
		 * below) transaction, and can't be split into two.
		 */
		DutyCompletionResult completion = self.getObject().completeDutyEntryAndFinalizeBooking(
				accessToken, request, dropLocation, photoName, receiptPhotos, ipAddress, userAgent);

		BookingEntry entry = completion.entry();
		GarageReturnEstimate garageReturn = completion.garageReturn();
		BigDecimal extraTotal = completion.extraTotal();

		accessToken.setLastUsedAt(completion.actualDropSubmittedAt());
		accessToken.setStatus(DriverDutyTokenStatus.COMPLETED);
		tokenRepository.save(accessToken);

		/*
		 * completeDutyEntryAndFinalizeBooking's transaction has already
		 * committed by the time control returns here (self.getObject() went
		 * through the Spring proxy, so the method boundary is real), so this
		 * is a fresh, non-lazy read -- not reuse of a detached entity from
		 * that closed session.
		 */
		Booking savedBooking = bookingRepository
				.findByBookingIdAndOrgId(completion.bookingId(), accessToken.getOrgId())
				.orElseThrow(() -> new NotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));

		BigDecimal amountToCollect = calculatePendingAmount(savedBooking);

		PaymentInstruction instruction;

		if (amountToCollect.compareTo(BigDecimal.ZERO) > 0) {
			try {
				boolean mockGateway = resolveEffectiveGateway(accessToken.getOrgId()) == PaymentGateway.MOCK;

				RazorpayQrPayload qrPayload = mockGateway
						? mockPaymentService.generateMockQr(
								accessToken.getOrgId(),
								savedBooking.getBookingId(),
								entry.getDutyId(),
								amountToCollect
						)
						: razorpayPaymentService.generateQR(
								accessToken.getOrgId(),
								savedBooking.getBookingId(),
								entry.getDutyId(),
								amountToCollect
						);

				String qrDisplayValue = firstNonBlank(
						qrPayload.getQrImageContent(),
						qrPayload.getQrImageUrl()
				);

				instruction = mockGateway
						? new PaymentInstruction(
								true,
								amountToCollect,
								qrDisplayValue,
								"MOCK_PAYMENT_AUTO_CONFIRMED",
								"Dummy payment auto-confirmed (MOCK gateway) -- no real money collected"
						)
						: new PaymentInstruction(
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

		int actualDrivenKm = request.odometerKm() - entry.getStartingKM();
		/*
		 * P0 display-vs-billing consistency fix -- this used to add
		 * garageReturn.rawDistanceKm() (the precise, un-rounded figure), while
		 * closingKM/the actual bill above are built from
		 * garageReturn.distanceKmRoundedUp() (CEILING-rounded, since closingKM
		 * is a whole-number odometer reading). That meant the "Projected total"
		 * figure shown to the driver could differ from the distance the bill
		 * was actually calculated from by up to ~1km -- exactly the "Chauffeur
		 * says 18.0 km, backend bills 19.0 km" inconsistency this feature must
		 * not have. Using the same rounded figure here makes the displayed
		 * projected total always equal to (actualDrivenKm + the distance
		 * actually billed), never a different, more-precise-looking number.
		 */
		double projectedTotalKm = actualDrivenKm + garageReturn.distanceKmRoundedUp();

		ReturnRouteEstimate returnRoute = new ReturnRouteEstimate(
				garageReturn.rawDistanceKm(),
				garageReturn.durationSeconds(),
				garageReturn.provider(),
				garageReturn.routeAvailable(),
				toGeoPoint(dropLocation),
				toGeoPoint(resolveGarageLocation(entry)),
				garageReturn.routeGeometry()
		);

		PackageFareBreakdown fareBreakdown = PackageFareBreakdownFactory.build(entry);

		GstSnapshot gstSnapshot = savedBooking.getGstSnapshot();

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
				amountToCollect,
				actualDrivenKm,
				projectedTotalKm,
				returnRoute,
				fareBreakdown,
				gstSnapshot != null ? gstSnapshot.getTotalTax() : null,
				gstSnapshot != null ? gstSnapshot.getGstRate() : null
		);

		return new DriverDutyEndResponse(
				true,
				entry.getStatus().name(),
				summary,
				instruction,
				"Duty completed successfully"
		);
	}

	private record DutyCompletionResult(
			BookingEntry entry,
			String bookingId,
			GarageReturnEstimate garageReturn,
			BigDecimal extraTotal,
			Instant actualDropSubmittedAt
	) {
	}

	/*
	 * The single atomic transaction for "mark this duty entry COMPLETED and,
	 * if it was the last open duty, complete the booking too." Must be called
	 * through the Spring proxy (submitEnd does this via self.getObject(), never
	 * directly) for @Transactional to actually apply.
	 *
	 * This used to be inlined in submitEnd itself, wrapped by submitEnd's own
	 * transaction all the way through payment-QR generation. That caused two
	 * separate bugs, both reproduced live against the dev backend while fixing
	 * the payment-QR MySQL lock-wait-timeout:
	 *
	 * 1. Lock timeout: submitEnd's own transaction stayed open through the QR
	 *    call, so any lock it held on the booking row was still held when QR
	 *    generation (its own REQUIRES_NEW transaction) needed a shared lock on
	 *    that same row for the Payment insert's FK check. Not a cycle MySQL's
	 *    deadlock detector can see (one side is just Java code waiting on the
	 *    other call to return), so it only ever resolved via
	 *    innodb_lock_wait_timeout.
	 *
	 * 2. Data corruption if fixed the *wrong* way: making ONLY the
	 *    booking-finalize step REQUIRES_NEW (to release its lock early) breaks
	 *    atomicity between the entry update and the booking completion -- if
	 *    anything in submitEnd fails AFTER that REQUIRES_NEW call returns
	 *    (which is exactly what happened here: a LazyInitializationException a
	 *    few lines later), the booking-completion sub-transaction had ALREADY
	 *    committed independently, leaving a live booking marked COMPLETED
	 *    whose only duty entry was rolled back to RUNNING. Reproduced directly
	 *    against the dev DB before this method existed.
	 *
	 * The fix for both: this method is the ONLY transaction boundary for the
	 * whole unit of work (entry lock/update + checkpoint + expenses + booking
	 * finalize), it fully commits before returning, and submitEnd only
	 * proceeds to QR generation afterward, with no ambient transaction of its
	 * own left open to conflict with it.
	 */
	@Transactional
	DutyCompletionResult completeDutyEntryAndFinalizeBooking(
			DriverDutyAccessToken accessToken,
			DriverDutyEndRequest request,
			AddressSnapshot dropLocation,
			String photoName,
			List<MultipartFile> receiptPhotos,
			String ipAddress,
			String userAgent
	) throws IOException {

		BookingEntry entry = lockEntryForDutyExecution(accessToken.getDutyId(), accessToken.getOrgId());

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

		/*
		 * P0 integrity guard -- pickup OTP verification is the backend's only
		 * real confirmation that the driver actually reached and picked up
		 * the client before the trip is billed and finalized. Checked here,
		 * against the entry's own authoritative pickupOtpVerifiedAt (never a
		 * client-supplied flag, never inferred from local Chauffeur
		 * navigation state), before any checkpoint/entry/booking mutation
		 * below -- a rejected completion must not partially finalize
		 * anything. This is the only place DutyStatus transitions to
		 * COMPLETED, so gating it here closes every completion path at once.
		 */
		if (entry.getPickupOtpVerifiedAt() == null) {
			throw new BusinessException(
					ErrorCode.PICKUP_OTP_NOT_VERIFIED,
					"Pickup OTP must be verified before this duty can be completed"
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

		bookingEntryRepository.save(entry);
		bookingService.finalizeBookingAfterDutyCompletion(
				booking.getBookingId(), entry.getDutyId(), anyOpenDuty, accessToken.getOrgId());

		return new DutyCompletionResult(
				entry, booking.getBookingId(), garageReturn, extraTotal, actualDropSubmittedAt);
	}

	/*
	 * =========================================================
	 * PAYMENT STATUS
	 * =========================================================
	 */

	@Transactional
	public QrPaymentStatusResponse checkQrPaymentStatus(String rawToken) {

		DriverDutyAccessToken accessToken = tokenValidator.resolveTokenForPaymentStatus(rawToken);
		BookingEntry entry = accessToken.getBookingEntry();
		Booking booking = entry.getBooking();

		/*
		 * Booking-level settlement check first -- covers a payment confirmed
		 * through a different flow than QR (cash, most notably), so a driver
		 * who chose Cash Received but the app restarted before navigating
		 * away sees "paid" here on resume (this is the screen
		 * reconcileActiveDuty's resume path lands on). Deliberately generic
		 * ("is the outstanding amount covered by ANY confirmed payment"),
		 * not cash-specific, and deliberately placed here rather than inside
		 * isPaidByQR/mockQrStatus themselves -- those stay untouched,
		 * QR-only, zero risk to existing digital-payment behavior.
		 */
		if (calculatePendingAmount(booking).compareTo(BigDecimal.ZERO) <= 0) {
			return buildSettledPaymentStatus(booking);
		}

		try {
			boolean mockGateway = resolveEffectiveGateway(accessToken.getOrgId()) == PaymentGateway.MOCK;

			return mockGateway
					? mockPaymentService.mockQrStatus(
							accessToken.getOrgId(),
							booking.getBookingId(),
							entry.getDutyId()
					)
					: razorpayPaymentService.isPaidByQR(
							accessToken.getOrgId(),
							booking.getBookingId(),
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

	private QrPaymentStatusResponse buildSettledPaymentStatus(Booking booking) {
		Payment latestConfirmed = booking.getPayments() == null
				? null
				: booking.getPayments().stream()
						.filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
						.max(java.util.Comparator.comparing(
								Payment::getTransactionDate,
								java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
						.orElse(null);

		return QrPaymentStatusResponse.builder()
				.status("PAID")
				.paid(true)
				.amount(latestConfirmed != null
						? latestConfirmed.getReceivedAmount().getAmount()
						: booking.getTotal().getAmount())
				.paymentId(latestConfirmed != null ? latestConfirmed.getId() : null)
				.paidAt(latestConfirmed != null ? latestConfirmed.getTransactionDate() : null)
				.message("Payment received")
				.build();
	}

	/*
	 * =========================================================
	 * CASH PAYMENT (driver-collected)
	 * =========================================================
	 * The backend-authoritative counterpart to the QR flow above -- a driver
	 * taps "Cash Received" only after physically collecting payment, and this
	 * records that as a real, CONFIRMED Payment row using the existing
	 * Payment model (PaymentMode.CASH, PaymentGateway.MANUAL_ENTRY), never a
	 * second parallel ledger. The amount is always the backend's own
	 * calculatePendingAmount at confirmation time -- the client submits
	 * nothing for this endpoint to trust.
	 */
	@Transactional
	public CashPaymentConfirmationResponse confirmCashPayment(String rawToken, String ipAddress, String userAgent) {

		DriverDutyAccessToken accessToken = tokenValidator.resolveTokenForPaymentStatus(rawToken);

		/*
		 * Same lock lockEntryForDutyExecution already uses for every other
		 * duty-execution write (org-scoped find, then PESSIMISTIC_WRITE by
		 * id) -- this is what makes the existence-check-then-insert below
		 * atomic with respect to a double-tap or a lost-response retry: a
		 * second concurrent call for the SAME dutyId blocks here until the
		 * first commits, and by then its own existence check (below) finds
		 * the row the first call just created.
		 */
		BookingEntry entry = lockEntryForDutyExecution(accessToken.getDutyId(), accessToken.getOrgId());

		if (entry.getStatus() != DutyStatus.COMPLETED) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"Duty must be completed before cash payment can be recorded (status: " + entry.getStatus() + ")"
			);
		}

		Booking booking = entry.getBooking();
		String orgId = accessToken.getOrgId();
		String bookingId = booking.getBookingId();
		String dutyId = entry.getDutyId();

		Payment existing = paymentRepo
				.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
						orgId, bookingId, DRIVER_DUTY_CASH_CONTEXT, dutyId)
				.orElse(null);

		if (existing != null) {
			return toCashConfirmationResponse(existing, "Cash payment already recorded");
		}

		/*
		 * Never trust a client-submitted amount -- there isn't one to trust
		 * in the first place, this endpoint accepts no request body. The
		 * authoritative payable amount is derived exactly the way
		 * submitEnd's QR generation already derives it.
		 */
		BigDecimal pending = calculatePendingAmount(booking);

		if (pending.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(
					ErrorCode.PAYMENT_ALREADY_CONFIRMED,
					"This booking is already fully paid -- no cash collection is required"
			);
		}

		Payment payment = new Payment();
		payment.setOrgId(orgId);
		payment.setBooking(booking);
		payment.setPaymentMode(PaymentMode.CASH);
		payment.setGateway(PaymentGateway.MANUAL_ENTRY);
		payment.setReceivedAmount(Money.INR(pending));
		payment.setTds(Money.INR(BigDecimal.ZERO));
		payment.setStatus(PaymentStatus.CONFIRMED);
		payment.setTransactionDate(Instant.now());
		payment.setCollectionContext(DRIVER_DUTY_CASH_CONTEXT);
		payment.setCollectionContextId(dutyId);
		payment.setCashCollectionReference(dutyId);
		payment.setRemarks("Cash collected by driver at duty close (ip " + ipAddress + ", " + userAgent + ")");
		payment.setCreatedAt(Instant.now());

		Payment saved;

		try {
			saved = paymentRepo.save(payment);
		} catch (DataIntegrityViolationException raceLoser) {
			/*
			 * Defensive backstop only -- the entry-row lock above should
			 * already make this unreachable. If it's ever hit anyway (e.g. a
			 * future refactor drops the lock), fail safe by returning the
			 * winning row instead of a confusing 500, never a duplicate.
			 */
			Payment winner = paymentRepo
					.findFirstByOrgIdAndBooking_BookingIdAndCollectionContextAndCollectionContextIdOrderByCreatedAtDesc(
							orgId, bookingId, DRIVER_DUTY_CASH_CONTEXT, dutyId)
					.orElseThrow(() -> raceLoser);
			return toCashConfirmationResponse(winner, "Cash payment already recorded");
		}

		PaymentConfirmedEvent event = paymentEventAssembler.toPaymentConfirmedEvent(booking, saved);
		eventPublisher.publishEvent(event);

		return toCashConfirmationResponse(saved, "Cash payment recorded");
	}

	private CashPaymentConfirmationResponse toCashConfirmationResponse(Payment payment, String message) {
		return new CashPaymentConfirmationResponse(
				true,
				payment.getId(),
				payment.getReceivedAmount().getAmount(),
				payment.getTransactionDate(),
				message
		);
	}

	/*
	 * =========================================================
	 * RETURN TO GARAGE / CLOSE DUTY
	 * =========================================================
	 * DutyStatus already reaches COMPLETED inside submitEnd above (fare +
	 * payment are already final at that point) -- these two steps don't
	 * transition any status. They record real, backend-persisted
	 * confirmations of what the driver actually reports next: physically
	 * arriving back at the garage, then closing the duty out. Reusing the
	 * existing DriverDutyCheckpoint entity/pattern (same as START/END)
	 * instead of inventing a parallel concept. resolveTokenForPaymentStatus,
	 * not resolveValidToken -- submitEnd already flips the token to
	 * COMPLETED and these two calls must keep working after that, the same
	 * carve-out payment polling already relies on.
	 */

	@Transactional
	public GarageReturnConfirmationResponse confirmGarageReturn(
			String rawToken,
			DriverDutyReturnGarageRequest request,
			String ipAddress,
			String userAgent
	) {
		DriverDutyAccessToken accessToken = tokenValidator.resolveTokenForPaymentStatus(rawToken);
		BookingEntry entry = lockEntryForDutyExecution(accessToken.getDutyId(), accessToken.getOrgId());

		if (entry.getStatus() != DutyStatus.COMPLETED) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"Complete the drop-off and payment before confirming garage return (status: " + entry.getStatus() + ")"
			);
		}

		Optional<DriverDutyCheckpoint> existing = checkpointRepository
				.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(entry.getId(), DriverDutyCheckpointType.GARAGE_RETURN);

		if (existing.isPresent()) {
			// Idempotent -- app restart / duplicate tap replays the same confirmation.
			return new GarageReturnConfirmationResponse(true, existing.get().getSubmittedAt());
		}

		AddressSnapshot location = request != null ? normalizeDriverLocation(request.location()) : null;
		Instant submittedAt = Instant.now();

		DriverDutyCheckpoint checkpoint = new DriverDutyCheckpoint();
		checkpoint.setOrgId(accessToken.getOrgId());
		checkpoint.setBookingId(entry.getBooking().getBookingId());
		checkpoint.setDutyId(entry.getDutyId());
		checkpoint.setDriverId(entry.getDriverId());
		checkpoint.setFleetVehicleId(entry.getFleetVehicleId());
		checkpoint.setBookingEntry(entry);
		checkpoint.setCheckpointType(DriverDutyCheckpointType.GARAGE_RETURN);
		checkpoint.setStatus(locationPresent(location)
				? DriverDutyCheckpointStatus.ACCEPTED
				: DriverDutyCheckpointStatus.NEEDS_REVIEW);
		checkpoint.setLocation(location);
		checkpoint.setAccuracyMeters(request != null ? request.accuracyMeters() : null);
		checkpoint.setLocationCapturedAt(request != null ? request.locationCapturedAt() : null);
		checkpoint.setSubmittedAt(submittedAt);
		checkpoint.setIpAddress(ipAddress);
		checkpoint.setUserAgent(userAgent);

		checkpointRepository.save(checkpoint);

		return new GarageReturnConfirmationResponse(true, submittedAt);
	}

	@Transactional
	public CloseDutyConfirmationResponse closeDutyFromDriverApp(String rawToken, String ipAddress, String userAgent) {
		DriverDutyAccessToken accessToken = tokenValidator.resolveTokenForPaymentStatus(rawToken);
		BookingEntry entry = lockEntryForDutyExecution(accessToken.getDutyId(), accessToken.getOrgId());

		if (entry.getStatus() != DutyStatus.COMPLETED) {
			throw new BusinessException(
					ErrorCode.INVALID_DUTY_STATUS,
					"Duty must be completed before it can be closed (status: " + entry.getStatus() + ")"
			);
		}

		boolean garageReturnConfirmed = checkpointRepository.existsByBookingEntry_IdAndCheckpointType(
				entry.getId(), DriverDutyCheckpointType.GARAGE_RETURN);

		if (!garageReturnConfirmed) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "Confirm garage return before closing this duty");
		}

		Optional<DriverDutyCheckpoint> existing = checkpointRepository
				.findFirstByBookingEntry_IdAndCheckpointTypeOrderBySubmittedAtDesc(entry.getId(), DriverDutyCheckpointType.CLOSE);

		if (existing.isPresent()) {
			/*
			 * Idempotent -- double-close is a no-op success, not an error.
			 * Deliberately checked (and short-circuits) BEFORE the payment
			 * guard below: a duty that was validly closed once must stay
			 * idempotently closeable forever afterward, even in the
			 * hypothetical case where its payment is later refunded --
			 * payment settlement is a precondition for the FIRST close, not
			 * something re-litigated on every replay of an already-successful
			 * close.
			 */
			return new CloseDutyConfirmationResponse(true, existing.get().getSubmittedAt());
		}

		/*
		 * P0 financial-integrity guard -- a duty must not be closeable while
		 * the booking still has an outstanding amount, regardless of
		 * collection method. Reuses the exact same calculatePendingAmount
		 * used to derive the QR/cash amount in the first place, so a
		 * prepaid, QR-paid, or cash-paid booking (any CONFIRMED payment,
		 * method-agnostic) is never incorrectly blocked -- only a booking
		 * with a genuinely unpaid balance is.
		 */
		BigDecimal pendingAmount = calculatePendingAmount(entry.getBooking());

		if (pendingAmount.compareTo(BigDecimal.ZERO) > 0) {
			throw new BusinessException(
					ErrorCode.PAYMENT_REQUIRED,
					"Outstanding payment of " + pendingAmount + " must be collected before this duty can be closed"
			);
		}

		Instant submittedAt = Instant.now();

		DriverDutyCheckpoint checkpoint = new DriverDutyCheckpoint();
		checkpoint.setOrgId(accessToken.getOrgId());
		checkpoint.setBookingId(entry.getBooking().getBookingId());
		checkpoint.setDutyId(entry.getDutyId());
		checkpoint.setDriverId(entry.getDriverId());
		checkpoint.setFleetVehicleId(entry.getFleetVehicleId());
		checkpoint.setBookingEntry(entry);
		checkpoint.setCheckpointType(DriverDutyCheckpointType.CLOSE);
		checkpoint.setStatus(DriverDutyCheckpointStatus.ACCEPTED);
		checkpoint.setSubmittedAt(submittedAt);
		checkpoint.setIpAddress(ipAddress);
		checkpoint.setUserAgent(userAgent);

		checkpointRepository.save(checkpoint);

		return new CloseDutyConfirmationResponse(true, submittedAt);
	}

	/*
	 * =========================================================
	 * LIVE LOCATION
	 * =========================================================
	 */

	/*
	 * resolveValidToken, not resolveTokenForPaymentStatus -- location has no
	 * reason to keep working after duty completion the way payment polling
	 * does. The token being ACTIVE alone doesn't guarantee the duty is still
	 * running (there's a window where the token is ACTIVE but the entry is
	 * ALLOTTED or already COMPLETED), so the entry's own status is checked
	 * separately below -- that's what actually enforces "only while a duty
	 * is genuinely running."
	 */
	@Transactional
	public DriverDutyLocationResponse submitLocationPing(String rawToken, DriverDutyLocationPingRequest payload) {

		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);
		BookingEntry entry = accessToken.getBookingEntry();

		if (entry.getStatus() != DutyStatus.RUNNING) {
			// Not persisted (nothing to track outside an active duty), but still echo the
			// raw fix back so a caller mid-transition (e.g. driver app) gets a well-formed
			// response rather than silently guessing why nothing came back.
			return new DriverDutyLocationResponse(
					entry.getDutyId(), payload.latitude(), payload.longitude(), payload.headingDegrees(),
					payload.capturedAt() != null ? payload.capturedAt() : Instant.now(),
					null, null, false
			);
		}

		Instant receivedAt = Instant.now();

		Optional<DriverDutyLiveLocation> existing = liveLocationRepository.findByDutyId(entry.getDutyId());

		// Captured before the new ping overwrites it below -- this is the real
		// prior GPS fix, used by FraudSignalService's GPS-jump check.
		AddressSnapshot previousLocation = existing
				.map(loc -> new AddressSnapshot("previous-ping", null, loc.getLatitude(), loc.getLongitude()))
				.orElse(null);
		Instant previousCapturedAt = existing.map(DriverDutyLiveLocation::getCapturedAt).orElse(null);

		DriverDutyLiveLocation location = existing
				.orElseGet(() -> {
					DriverDutyLiveLocation created = new DriverDutyLiveLocation();
					created.setOrgId(accessToken.getOrgId());
					created.setBookingId(entry.getBooking().getBookingId());
					created.setDutyId(entry.getDutyId());
					created.setDriverId(entry.getDriverId());
					created.setBookingEntry(entry);
					return created;
				});

		location.setLatitude(payload.latitude());
		location.setLongitude(payload.longitude());
		location.setAccuracyMeters(payload.accuracyMeters());
		location.setHeadingDegrees(payload.headingDegrees());
		location.setSpeedMps(payload.speedMps());
		location.setCapturedAt(payload.capturedAt() != null ? payload.capturedAt() : receivedAt);
		location.setReceivedAt(receivedAt);

		liveLocationRepository.save(location);

		if (payload.latitude() != null && payload.longitude() != null) {
			fraudSignalService.checkGpsJump(
					accessToken.getOrgId(), entry.getDriverId(), entry.getDutyId(),
					previousLocation, previousCapturedAt,
					payload.latitude(), payload.longitude(), location.getCapturedAt()
			);
		}

		/*
		 * DutyStatus.RUNNING spans both the garage->pickup leg AND the
		 * pickup->drop leg -- it flips at submitStart, not at pickup OTP
		 * verification. Targeting dropLocation unconditionally meant a
		 * driver still on the way to pickup got an ETA/distance-remaining
		 * against their eventual drop-off instead of the pickup point they
		 * were actually headed to -- and this response is broadcast live to
		 * the customer app's tracking map (DutyLocationChannelRegistry),
		 * so that wrong figure was customer-visible, not just internal.
		 */
		AddressSnapshot etaTarget = entry.getPickupOtpVerifiedAt() == null
				? entry.getReportingLocation()
				: entry.getDropLocation();

		EtaEstimator.Estimate eta = EtaEstimator.estimate(
				etaTarget,
				location.getLatitude(),
				location.getLongitude(),
				location.getSpeedMps()
		);

		DriverDutyLocationResponse response = new DriverDutyLocationResponse(
				entry.getDutyId(),
				location.getLatitude(),
				location.getLongitude(),
				location.getHeadingDegrees(),
				location.getCapturedAt(),
				eta.distanceRemainingKm(),
				eta.etaMinutes(),
				true
		);

		dutyLocationChannelRegistry.broadcast(entry.getDutyId(), response);

		return response;
	}

	/*
	 * Same reasoning as ClientPaymentController.resolveEffectiveGateway: the org's
	 * actual configured gateway decides, not a hardcoded assumption of RAZORPAY. An
	 * org with no PaymentGatewayConfig row at all (today's default) falls back to
	 * RAZORPAY, so this is a no-op for every org that existed before MOCK did.
	 */
	private PaymentGateway resolveEffectiveGateway(String orgId) {
		return paymentGatewayConfigService.getRuntimeConfig(orgId)
				.map(config -> config.gateway())
				.orElse(PaymentGateway.RAZORPAY);
	}

	/*
	 * =========================================================
	 * HELPERS
	 * =========================================================
	 */

	/*
	 * Authorizes (org check) via the unlocked, booking-joined query first, then
	 * takes the PESSIMISTIC_WRITE lock by id only -- never through a query that
	 * joins booking, since MariaDB's FOR UPDATE has no "OF" clause to scope the
	 * lock to just the entry table. See BookingEntryRepository.lockById.
	 */
	private BookingEntry lockEntryForDutyExecution(String dutyId, String orgId) {
		BookingEntry authorized = bookingEntryRepository
				.findByDutyIdAndOrgId(dutyId, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		return bookingEntryRepository
				.lockById(authorized.getId())
				.orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));
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

	@SuppressWarnings("null")
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

	private static String generate6DigitOtp() {
		return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
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

	private GeoPoint toGeoPoint(AddressSnapshot location) {
		if (!locationPresent(location)) {
			return null;
		}

		return new GeoPoint(location.getLatitude(), location.getLongitude());
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

			/*
			 * P0 financial-integrity guard -- this distance feeds directly
			 * into closingKM (below, via billableClosingKm in submitEnd) and
			 * from there into BookingUtil.calculateTotal's extraChargeableDistance,
			 * i.e. it can directly create/increase a customer's bill.
			 * result.estimated() is true for BOTH a haversine straight-line
			 * guess (FallbackGeoProvider) AND a stale, past-TTL cache entry
			 * served only because live computation just failed
			 * (RouteCacheService) -- neither is authoritative for billing,
			 * only for non-financial display. Treated exactly like a total
			 * geo-calculation failure: no estimated garage-return distance is
			 * added to the bill, so the driver's own verified odometer
			 * reading (already captured, added separately in submitEnd)
			 * remains the sole billed distance for this leg. Operational
			 * route-leg display (getRouteForLeg) is unaffected -- it never
			 * gates on this flag, by design.
			 */
			if (result.estimated()) {
				log.warn(
						"Garage-return distance for duty {} was estimated (stale cache or haversine fallback, provider={}) "
								+ "-- excluding it from the billable distance rather than risking an incorrect customer charge",
						entry.getDutyId(), result.provider());

				return GarageReturnEstimate.zero();
			}

			int distanceKmRoundedUp = BigDecimal.valueOf(result.distanceKm())
					.setScale(0, RoundingMode.CEILING)
					.intValue();

			boolean routeAvailable = result.routeGeometry() != null && !result.routeGeometry().isEmpty();

			return new GarageReturnEstimate(
					distanceKmRoundedUp,
					result.durationSeconds(),
					result.distanceKm(),
					result.provider(),
					routeAvailable,
					result.routeGeometry()
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

	@SuppressWarnings("null")
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

	private record GarageReturnEstimate(
			int distanceKmRoundedUp,
			long durationSeconds,
			double rawDistanceKm,
			String provider,
			boolean routeAvailable,
			List<GeoPoint> routeGeometry
	) {
		static GarageReturnEstimate zero() {
			return new GarageReturnEstimate(0, 0, 0.0, null, false, null);
		}
	}
}
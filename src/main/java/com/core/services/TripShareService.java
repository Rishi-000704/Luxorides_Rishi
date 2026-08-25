package com.core.services;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.client.app.TripShareLinkResponse;
import com.core.dtos.pub.PublicTripStatusResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.BookingEntry;
import com.core.models.TripShareLink;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyLiveLocationRepository;
import com.core.repositories.TripShareLinkRepository;
import com.core.util.EtaEstimator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripShareService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final BookingEntryRepository bookingEntryRepository;
	private final TripShareLinkRepository shareLinkRepository;
	private final DriverDutyLiveLocationRepository liveLocationRepository;
	private final DriverDutyTokenValidator tokenValidator;

	/*
	 * ===================== CLIENT SIDE: CREATE LINK =====================
	 */
	@Transactional
	public TripShareLinkResponse createShareLink(String dutyId, String clientId, String orgId) {
		BookingEntry entry = bookingEntryRepository.findByDutyIdAndOrgId(dutyId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

		if (!clientId.equals(entry.getBooking().getClientId())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "This duty does not belong to you");
		}

		String rawToken = generateRawToken();

		TripShareLink link = new TripShareLink();
		link.setOrgId(orgId);
		link.setBookingId(entry.getBooking().getBookingId());
		link.setDutyId(dutyId);
		link.setBookingEntry(entry);
		link.setTokenHash(tokenValidator.hash(rawToken));
		link.setExpiresAt(Instant.now().plus(Duration.ofHours(48)));
		link.setRevoked(false);

		TripShareLink saved = shareLinkRepository.save(link);

		return new TripShareLinkResponse(rawToken, saved.getExpiresAt());
	}

	/*
	 * ===================== PUBLIC SIDE: RESOLVE =====================
	 */
	@Transactional(readOnly = true)
	public PublicTripStatusResponse getPublicStatus(String rawToken) {
		TripShareLink link = shareLinkRepository.findByTokenHash(tokenValidator.hash(rawToken))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_SHARE_LINK_INVALID, "Invalid tracking link"));

		if (link.isRevoked() || link.getExpiresAt().isBefore(Instant.now())) {
			throw new BusinessException(ErrorCode.TRIP_SHARE_LINK_INVALID, "This tracking link has expired");
		}

		BookingEntry entry = link.getBookingEntry();

		String driverFirstName = entry.getDriver() != null && entry.getDriver().getName() != null
				? firstName(entry.getDriver().getName().getDisplayName())
				: null;

		String vehicleName = entry.getAllotedVehicle() != null && entry.getAllotedVehicle().getMasterVehicle() != null
				? entry.getAllotedVehicle().getMasterVehicle().getName()
				: null;

		String vehicleNumber = entry.getAllotedVehicle() != null
				? entry.getAllotedVehicle().getRegistrationNumber()
				: null;

		return liveLocationRepository.findByDutyId(entry.getDutyId())
				.map(location -> {
					EtaEstimator.Estimate eta = EtaEstimator.estimate(
							entry.getDropLocation(),
							location.getLatitude(),
							location.getLongitude(),
							location.getSpeedMps()
					);

					return new PublicTripStatusResponse(
							entry.getStatus(),
							driverFirstName,
							vehicleName,
							vehicleNumber,
							location.getLatitude(),
							location.getLongitude(),
							location.getHeadingDegrees(),
							location.getCapturedAt(),
							eta.distanceRemainingKm(),
							eta.etaMinutes()
					);
				})
				.orElseGet(() -> new PublicTripStatusResponse(
						entry.getStatus(), driverFirstName, vehicleName, vehicleNumber,
						null, null, null, null, null, null
				));
	}

	private String firstName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return null;
		}

		int spaceIndex = displayName.trim().indexOf(' ');
		return spaceIndex > 0 ? displayName.trim().substring(0, spaceIndex) : displayName.trim();
	}

	private String generateRawToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);

		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}

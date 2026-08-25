package com.core.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.fraud.FraudSignalResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.FraudSignal;
import com.core.models.Org;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.enums.FraudSignalStatus;
import com.core.models.enums.FraudSignalType;
import com.core.repositories.BookingRepository;
import com.core.repositories.FraudSignalRepository;
import com.core.repositories.OrgRepository;
import com.core.util.EtaEstimator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * Rule-based anomaly detection over real data (booking counts, cancellation
 * counts, GPS deltas) -- see the class comment on FraudSignal for the
 * human-in-the-loop guarantee. Two detectors run on a schedule across every
 * org (DUPLICATE_RAPID_BOOKINGS, REPEATED_LAST_MINUTE_CANCELLATIONS); the
 * third (IMPOSSIBLE_GPS_JUMP) runs inline, called from
 * ExternalDriverDutyService.submitLocationPing on every real ping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudSignalService {

	private static final long RAPID_BOOKING_THRESHOLD = 5;
	private static final Duration RAPID_BOOKING_WINDOW = Duration.ofHours(24);

	private static final long REPEATED_CANCELLATION_THRESHOLD = 3;
	private static final Duration CANCELLATION_WINDOW = Duration.ofDays(30);

	// A chauffeur-driven car cannot plausibly exceed this between two
	// consecutive real GPS pings -- a jump beyond it means either a GPS
	// glitch or a spoofed/faked location, either way worth a human look.
	private static final double IMPLAUSIBLE_SPEED_KMH = 180.0;

	private final FraudSignalRepository fraudSignalRepository;
	private final BookingRepository bookingRepository;
	private final OrgRepository orgRepository;

	@Scheduled(fixedDelay = 3_600_000) // hourly -- these are historical-pattern scans, not real-time
	@Transactional
	public void scanAllOrgs() {
		for (Org org : orgRepository.findAll()) {
			try {
				scanRapidBookings(org.getOrgId());
				scanRepeatedCancellations(org.getOrgId());
			} catch (Exception ex) {
				log.warn("Fraud scan failed for org {}: {}", org.getOrgId(), ex.getMessage());
			}
		}
	}

	@Transactional
	public void scanRapidBookings(String orgId) {
		Instant since = Instant.now().minus(RAPID_BOOKING_WINDOW);

		for (Object[] row : bookingRepository.findClientsWithRapidBookings(orgId, since, RAPID_BOOKING_THRESHOLD)) {
			String clientId = (String) row[0];
			long count = ((Number) row[1]).longValue();

			raiseIfNew(orgId, FraudSignalType.DUPLICATE_RAPID_BOOKINGS, clientId, null, null, null,
					clientId + " made " + count + " bookings in the last 24 hours");
		}
	}

	@Transactional
	public void scanRepeatedCancellations(String orgId) {
		Instant since = Instant.now().minus(CANCELLATION_WINDOW);

		for (Object[] row : bookingRepository.findClientsWithRepeatedCancellations(orgId, since, REPEATED_CANCELLATION_THRESHOLD)) {
			String clientId = (String) row[0];
			long count = ((Number) row[1]).longValue();

			raiseIfNew(orgId, FraudSignalType.REPEATED_LAST_MINUTE_CANCELLATIONS, clientId, null, null, null,
					clientId + " cancelled " + count + " bookings in the last 30 days");
		}
	}

	/*
	 * Called from ExternalDriverDutyService.submitLocationPing BEFORE the new
	 * ping overwrites the previous one -- previousLocation is the real prior
	 * GPS fix, still in the DB at the moment this runs.
	 */
	@Transactional
	public void checkGpsJump(
			String orgId, String driverId, String dutyId,
			AddressSnapshot previousLocation, Instant previousCapturedAt,
			double newLatitude, double newLongitude, Instant newCapturedAt
	) {
		if (previousLocation == null || previousCapturedAt == null
				|| previousLocation.getLatitude() == null || previousLocation.getLongitude() == null) {
			return;
		}

		long elapsedSeconds = Duration.between(previousCapturedAt, newCapturedAt).getSeconds();
		if (elapsedSeconds <= 0) {
			return;
		}

		EtaEstimator.Estimate estimate = EtaEstimator.estimate(
				new AddressSnapshot("gps-jump-check", null, newLatitude, newLongitude),
				previousLocation.getLatitude(), previousLocation.getLongitude(), null);

		if (estimate.distanceRemainingKm() == null) {
			return;
		}

		double impliedSpeedKmh = estimate.distanceRemainingKm() / (elapsedSeconds / 3600.0);

		if (impliedSpeedKmh > IMPLAUSIBLE_SPEED_KMH) {
			raiseIfNew(orgId, FraudSignalType.IMPOSSIBLE_GPS_JUMP, null, driverId, null, dutyId,
					String.format("Implied speed %.0f km/h between consecutive GPS pings on duty %s", impliedSpeedKmh, dutyId));
		}
	}

	@Transactional(readOnly = true)
	public List<FraudSignalResponse> list(String orgId) {
		return fraudSignalRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream().map(this::toResponse).toList();
	}

	@Transactional
	public FraudSignalResponse review(String id, String orgId, String reviewedBy, boolean dismiss) {
		FraudSignal signal = fraudSignalRepository.findByIdAndOrgId(id, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Fraud signal not found"));

		signal.setStatus(dismiss ? FraudSignalStatus.DISMISSED : FraudSignalStatus.REVIEWED);
		signal.setReviewedBy(reviewedBy);
		signal.setReviewedAt(Instant.now());

		return toResponse(fraudSignalRepository.save(signal));
	}

	private void raiseIfNew(
			String orgId, FraudSignalType type, String clientId, String driverId, String bookingId, String dutyId,
			String description) {

		boolean alreadyOpen = clientId != null
				? fraudSignalRepository.existsByOrgIdAndTypeAndClientIdAndStatus(orgId, type, clientId, FraudSignalStatus.OPEN)
				: fraudSignalRepository.existsByOrgIdAndTypeAndDriverIdAndDutyIdAndStatus(orgId, type, driverId, dutyId, FraudSignalStatus.OPEN);

		if (alreadyOpen) {
			return;
		}

		FraudSignal signal = new FraudSignal();
		signal.setOrgId(orgId);
		signal.setType(type);
		signal.setClientId(clientId);
		signal.setDriverId(driverId);
		signal.setBookingId(bookingId);
		signal.setDutyId(dutyId);
		signal.setDescription(description);
		signal.setStatus(FraudSignalStatus.OPEN);

		fraudSignalRepository.save(signal);
	}

	private FraudSignalResponse toResponse(FraudSignal s) {
		return new FraudSignalResponse(s.getId(), s.getType(), s.getClientId(), s.getDriverId(), s.getBookingId(),
				s.getDutyId(), s.getDescription(), s.getStatus(), s.getReviewedBy(), s.getReviewedAt(), s.getCreatedAt());
	}
}

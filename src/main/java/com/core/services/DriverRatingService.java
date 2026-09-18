package com.core.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.driver.DriverOpsRatingRequest;
import com.core.dtos.driver.DriverOpsRatingResponse;
import com.core.dtos.driver.DriverRatingSummaryResponse;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Driver;
import com.core.models.DriverOpsRating;
import com.core.repositories.DriverOpsRatingRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.TripRatingRepository;
import com.core.services.common.AuditActorService;

import lombok.RequiredArgsConstructor;

/*
 * Real driver rating, combining two genuinely separate sources -- never a
 * fabricated single number:
 *   - TripRating: the customer's own per-duty rating (existing, unchanged).
 *   - DriverOpsRating: ops's own current assessment of the driver (new).
 * combinedAverageRating is a simple average of the two when both exist,
 * falls back to whichever exists when only one does, and is honestly null
 * when neither does yet.
 */
@Service
@RequiredArgsConstructor
public class DriverRatingService {

	private final TripRatingRepository tripRatingRepository;
	private final DriverOpsRatingRepository opsRatingRepository;
	private final DriverRepository driverRepository;
	private final AuditActorService auditActorService;

	@Transactional(readOnly = true)
	public DriverOpsRatingResponse getOpsRating(String orgId, String driverId) {
		resolveDriver(orgId, driverId);

		return opsRatingRepository.findByOrgIdAndDriverId(orgId, driverId)
				.map(this::toResponse)
				.orElse(new DriverOpsRatingResponse(null, null, null, null));
	}

	// updatedBy/updatedAt are populated automatically by JPA auditing
	// (AuditorAwareImpl, off the real security context) -- not set manually
	// here, the same as every other AuditableEntity in this codebase.
	@Transactional
	public DriverOpsRatingResponse setOpsRating(String orgId, String driverId, DriverOpsRatingRequest request) {
		resolveDriver(orgId, driverId);

		DriverOpsRating rating = opsRatingRepository.findByOrgIdAndDriverId(orgId, driverId)
				.orElseGet(() -> {
					DriverOpsRating created = new DriverOpsRating();
					created.setOrgId(orgId);
					created.setDriverId(driverId);
					return created;
				});

		rating.setStars(request.stars());
		rating.setComment(request.comment());

		return toResponse(opsRatingRepository.save(rating));
	}

	@Transactional(readOnly = true)
	public DriverRatingSummaryResponse getRatingSummary(String orgId, String driverId) {
		Double clientAverage = tripRatingRepository.findAverageStarsByDriverIdAndOrgId(driverId, orgId);
		long clientCount = tripRatingRepository.countByDriverIdAndOrgId(driverId, orgId);
		Integer opsStars = opsRatingRepository.findByOrgIdAndDriverId(orgId, driverId)
				.map(DriverOpsRating::getStars)
				.orElse(null);

		Double combined;
		if (clientAverage != null && opsStars != null) {
			combined = (clientAverage + opsStars) / 2.0;
		} else if (clientAverage != null) {
			combined = clientAverage;
		} else if (opsStars != null) {
			combined = opsStars.doubleValue();
		} else {
			combined = null;
		}

		return new DriverRatingSummaryResponse(clientAverage, clientCount, opsStars, combined);
	}

	private DriverOpsRatingResponse toResponse(DriverOpsRating rating) {
		return new DriverOpsRatingResponse(
				rating.getStars(),
				rating.getComment(),
				rating.getUpdatedAt(),
				auditActorService.resolve(rating.getUpdatedBy()).displayName()
		);
	}

	private Driver resolveDriver(String orgId, String driverId) {
		return driverRepository.findByIdAndOrgId(driverId, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DRIVER_NOT_FOUND, "Driver not found"));
	}
}

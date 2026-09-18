package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.driver.DriverOpsRatingRequest;
import com.core.dtos.driver.DriverRatingSummaryResponse;
import com.core.models.Driver;
import com.core.models.DriverOpsRating;
import com.core.repositories.DriverOpsRatingRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.TripRatingRepository;
import com.core.dtos.auth.AuditActorDTO;
import com.core.services.common.AuditActorService;

/*
 * Combined driver rating = client's TripRating aggregate + ops's own
 * DriverOpsRating, per the user's explicit requirement: the driver-facing
 * average must reflect both sources, never fabricate a number when one or
 * both are missing, and never leak the raw fare/earnings figures this
 * replaced on the Activity screen.
 */
class DriverRatingServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String DRIVER_ID = "driver-1";

	private TripRatingRepository tripRatingRepository;
	private DriverOpsRatingRepository opsRatingRepository;
	private DriverRepository driverRepository;
	private AuditActorService auditActorService;
	private DriverRatingService service;

	@BeforeEach
	void setUp() {
		tripRatingRepository = mock(TripRatingRepository.class);
		opsRatingRepository = mock(DriverOpsRatingRepository.class);
		driverRepository = mock(DriverRepository.class);
		auditActorService = mock(AuditActorService.class);
		service = new DriverRatingService(tripRatingRepository, opsRatingRepository, driverRepository, auditActorService);

		Driver driver = new Driver();
		driver.setId(DRIVER_ID);
		driver.setOrgId(ORG_ID);
		when(driverRepository.findByIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(Optional.of(driver));
	}

	@Test
	void combinedRating_averagesClientAndOps_whenBothExist() {
		when(tripRatingRepository.findAverageStarsByDriverIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(4.0);
		when(tripRatingRepository.countByDriverIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(10L);
		DriverOpsRating opsRating = new DriverOpsRating();
		opsRating.setStars(5);
		when(opsRatingRepository.findByOrgIdAndDriverId(ORG_ID, DRIVER_ID)).thenReturn(Optional.of(opsRating));

		DriverRatingSummaryResponse response = service.getRatingSummary(ORG_ID, DRIVER_ID);

		assertEquals(4.0, response.clientAverageRating());
		assertEquals(10L, response.clientRatingCount());
		assertEquals(5, response.opsRating());
		assertEquals(4.5, response.combinedAverageRating());
	}

	@Test
	void combinedRating_fallsBackToClientOnly_whenOpsHasNotRatedYet() {
		when(tripRatingRepository.findAverageStarsByDriverIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(3.5);
		when(tripRatingRepository.countByDriverIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(2L);
		when(opsRatingRepository.findByOrgIdAndDriverId(ORG_ID, DRIVER_ID)).thenReturn(Optional.empty());

		DriverRatingSummaryResponse response = service.getRatingSummary(ORG_ID, DRIVER_ID);

		assertEquals(3.5, response.combinedAverageRating());
		assertNull(response.opsRating());
	}

	@Test
	void combinedRating_fallsBackToOpsOnly_whenNoCustomerHasRatedYet() {
		when(tripRatingRepository.findAverageStarsByDriverIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(null);
		when(tripRatingRepository.countByDriverIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(0L);
		DriverOpsRating opsRating = new DriverOpsRating();
		opsRating.setStars(3);
		when(opsRatingRepository.findByOrgIdAndDriverId(ORG_ID, DRIVER_ID)).thenReturn(Optional.of(opsRating));

		DriverRatingSummaryResponse response = service.getRatingSummary(ORG_ID, DRIVER_ID);

		assertEquals(3.0, response.combinedAverageRating());
		assertNull(response.clientAverageRating());
	}

	@Test
	void combinedRating_isHonestlyNull_whenNeitherSourceHasRatedYet() {
		when(tripRatingRepository.findAverageStarsByDriverIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(null);
		when(tripRatingRepository.countByDriverIdAndOrgId(DRIVER_ID, ORG_ID)).thenReturn(0L);
		when(opsRatingRepository.findByOrgIdAndDriverId(ORG_ID, DRIVER_ID)).thenReturn(Optional.empty());

		DriverRatingSummaryResponse response = service.getRatingSummary(ORG_ID, DRIVER_ID);

		assertNull(response.combinedAverageRating());
	}

	@Test
	void setOpsRating_createsRating_whenNoneExistsYet() {
		when(opsRatingRepository.findByOrgIdAndDriverId(ORG_ID, DRIVER_ID)).thenReturn(Optional.empty());
		when(opsRatingRepository.save(org.mockito.ArgumentMatchers.any(DriverOpsRating.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(auditActorService.resolve(org.mockito.ArgumentMatchers.any()))
				.thenReturn(new AuditActorDTO("emp-1", "Amit Mishra", "EMPLOYEE"));

		var response = service.setOpsRating(ORG_ID, DRIVER_ID, new DriverOpsRatingRequest(4, "Punctual and courteous"));

		assertEquals(4, response.stars());
		assertEquals("Punctual and courteous", response.comment());
	}

	@Test
	void setOpsRating_updatesExistingRating_ratherThanCreatingASecondRow() {
		DriverOpsRating existing = new DriverOpsRating();
		existing.setId("existing-id");
		existing.setOrgId(ORG_ID);
		existing.setDriverId(DRIVER_ID);
		existing.setStars(2);
		when(opsRatingRepository.findByOrgIdAndDriverId(ORG_ID, DRIVER_ID)).thenReturn(Optional.of(existing));
		when(opsRatingRepository.save(org.mockito.ArgumentMatchers.any(DriverOpsRating.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(auditActorService.resolve(org.mockito.ArgumentMatchers.any()))
				.thenReturn(new AuditActorDTO("emp-1", "Amit Mishra", "EMPLOYEE"));

		var response = service.setOpsRating(ORG_ID, DRIVER_ID, new DriverOpsRatingRequest(5, "Improved a lot"));

		assertEquals("existing-id", existing.getId());
		assertEquals(5, response.stars());
	}
}

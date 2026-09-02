package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.client.app.TripRatingResponse;
import com.core.models.TripRating;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.DriverDutyLiveLocationRepository;
import com.core.repositories.TripRatingRepository;
import com.core.services.config.OrgService;

/*
 * P1.8 -- ClientBookingService.getRating previously only checked orgId, so
 * any authenticated client in the same org could read another client's trip
 * rating (stars + comment) by guessing/knowing a dutyId. Now filtered by the
 * requesting client's own clientId, same ownership check submitRating
 * already applies on write.
 */
class ClientBookingServiceRatingTest {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String OWNING_CLIENT_ID = "client-owner";
	private static final String OTHER_CLIENT_ID = "client-other";

	private TripRatingRepository tripRatingRepository;
	private ClientBookingService service;

	@BeforeEach
	void setUp() {
		BookingService bookingService = mock(BookingService.class);
		BookingEntryRepository bookingEntryRepository = mock(BookingEntryRepository.class);
		DriverDutyLiveLocationRepository liveLocationRepository = mock(DriverDutyLiveLocationRepository.class);
		tripRatingRepository = mock(TripRatingRepository.class);
		CancellationPolicyService cancellationPolicyService = mock(CancellationPolicyService.class);
		OrgService orgService = mock(OrgService.class);

		service = new ClientBookingService(
				bookingService, bookingEntryRepository, liveLocationRepository,
				tripRatingRepository, cancellationPolicyService, orgService);
	}

	private TripRating ratingOwnedBy(String clientId) {
		TripRating rating = new TripRating();
		rating.setOrgId(ORG_ID);
		rating.setDutyId(DUTY_ID);
		rating.setClientId(clientId);
		rating.setStars(5);
		rating.setComment("Great ride");
		rating.setCreatedAt(Instant.now());
		return rating;
	}

	@Test
	void getRating_ownerRequestsTheirOwnRating_returnsIt() {
		when(tripRatingRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID))
				.thenReturn(Optional.of(ratingOwnedBy(OWNING_CLIENT_ID)));

		Optional<TripRatingResponse> result = service.getRating(DUTY_ID, OWNING_CLIENT_ID, ORG_ID);

		assertTrue(result.isPresent());
		assertEquals(5, result.get().stars());
	}

	@Test
	void getRating_anotherClientInSameOrgRequestsIt_returnsEmpty_notTheOtherClientsRating() {
		when(tripRatingRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID))
				.thenReturn(Optional.of(ratingOwnedBy(OWNING_CLIENT_ID)));

		Optional<TripRatingResponse> result = service.getRating(DUTY_ID, OTHER_CLIENT_ID, ORG_ID);

		assertTrue(result.isEmpty());
	}

	@Test
	void getRating_noRatingExists_returnsEmpty() {
		when(tripRatingRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.empty());

		Optional<TripRatingResponse> result = service.getRating(DUTY_ID, OWNING_CLIENT_ID, ORG_ID);

		assertTrue(result.isEmpty());
	}
}

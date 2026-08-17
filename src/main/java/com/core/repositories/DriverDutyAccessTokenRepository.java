package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DriverDutyAccessToken;
import com.core.models.enums.DriverDutyTokenStatus;

@Repository
public interface DriverDutyAccessTokenRepository extends JpaRepository<DriverDutyAccessToken, String> {

	Optional<DriverDutyAccessToken> findByTokenHash(String tokenHash);

	Optional<DriverDutyAccessToken> findFirstByBookingEntry_IdAndStatusOrderByCreatedAtDesc(
		String bookingEntryId,
		DriverDutyTokenStatus status
	);
	
	Optional<DriverDutyAccessToken> findFirstByBookingEntry_IdOrderByCreatedAtDesc(
			String bookingEntryId
	);
}
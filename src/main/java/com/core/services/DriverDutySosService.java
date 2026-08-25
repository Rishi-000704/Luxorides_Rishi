package com.core.services;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.driverduty.DriverDutySosRequest;
import com.core.dtos.driverduty.DriverDutySosResponse;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutySosAlert;
import com.core.repositories.DriverDutySosAlertRepository;

import lombok.RequiredArgsConstructor;

/*
 * No RUNNING-status gate, unlike location pings -- a driver may need to
 * raise an SOS outside an active-duty window too (e.g. waiting at pickup,
 * returning to garage). resolveValidToken, not resolveTokenForPaymentStatus:
 * this only needs the token to identify who/which duty, not payment state.
 */
@Service
@RequiredArgsConstructor
public class DriverDutySosService {

	private final DriverDutyTokenValidator tokenValidator;
	private final DriverDutySosAlertRepository sosAlertRepository;

	@Transactional
	public DriverDutySosResponse submitSos(
			String rawToken,
			DriverDutySosRequest payload,
			String ipAddress,
			String userAgent
	) {
		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);
		BookingEntry entry = accessToken.getBookingEntry();

		DriverDutySosAlert alert = new DriverDutySosAlert();
		alert.setOrgId(accessToken.getOrgId());
		alert.setBookingId(entry.getBooking().getBookingId());
		alert.setDutyId(entry.getDutyId());
		alert.setDriverId(entry.getDriverId());
		alert.setBookingEntry(entry);
		alert.setLatitude(payload.latitude());
		alert.setLongitude(payload.longitude());
		alert.setCapturedAt(payload.capturedAt() != null ? payload.capturedAt() : Instant.now());
		alert.setNotes(payload.notes());
		alert.setIpAddress(ipAddress);
		alert.setUserAgent(userAgent);

		DriverDutySosAlert saved = sosAlertRepository.save(alert);

		return new DriverDutySosResponse(saved.getId(), true);
	}
}

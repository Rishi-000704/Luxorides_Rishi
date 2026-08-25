package com.core.services;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.DriverDutyIncidentRequest;
import com.core.dtos.driverduty.DriverDutyIncidentResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.DriverDutyIncidentReport;
import com.core.repositories.DriverDutyIncidentReportRepository;
import com.core.services.common.FileService;

import lombok.RequiredArgsConstructor;

/*
 * No RUNNING-status gate and no WS broadcast -- same reasoning as
 * DriverDutySosService. An incident is a discrete authored event with up to
 * 3 optional photos, matching DriverDutyCheckpoint's odometerPhoto storage
 * convention (a plain filename column, saved via FileService).
 */
@Service
@RequiredArgsConstructor
public class DriverDutyIncidentService {

	private static final long MAX_PHOTO_SIZE = 10L * 1024L * 1024L;

	private final DriverDutyTokenValidator tokenValidator;
	private final DriverDutyIncidentReportRepository incidentReportRepository;
	private final FileService fileService;

	@Transactional
	public DriverDutyIncidentResponse submitIncident(
			String rawToken,
			DriverDutyIncidentRequest payload,
			List<MultipartFile> photos,
			String ipAddress,
			String userAgent
	) throws IOException {
		if (payload.category() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Incident category is required");
		}
		if (payload.description() == null || payload.description().isBlank()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Incident description is required");
		}
		if (payload.location() == null || payload.location().getFormattedAddress() == null
				|| payload.location().getFormattedAddress().isBlank()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Incident location is required");
		}

		DriverDutyAccessToken accessToken = tokenValidator.resolveValidToken(rawToken);
		BookingEntry entry = accessToken.getBookingEntry();

		DriverDutyIncidentReport report = new DriverDutyIncidentReport();
		report.setOrgId(accessToken.getOrgId());
		report.setBookingId(entry.getBooking().getBookingId());
		report.setDutyId(entry.getDutyId());
		report.setDriverId(entry.getDriverId());
		report.setBookingEntry(entry);
		report.setCategory(payload.category());
		report.setDescription(payload.description());
		report.setLocation(payload.location());
		report.setSubmittedAt(payload.submittedAt() != null ? payload.submittedAt() : Instant.now());
		report.setIpAddress(ipAddress);
		report.setUserAgent(userAgent);

		if (photos != null) {
			if (photos.size() > 3) {
				throw new BusinessException(ErrorCode.BAD_REQUEST, "At most 3 photos are allowed");
			}
			if (photos.size() > 0) {
				report.setPhoto1(saveImage(photos.get(0)));
			}
			if (photos.size() > 1) {
				report.setPhoto2(saveImage(photos.get(1)));
			}
			if (photos.size() > 2) {
				report.setPhoto3(saveImage(photos.get(2)));
			}
		}

		DriverDutyIncidentReport saved = incidentReportRepository.save(report);

		return new DriverDutyIncidentResponse(saved.getId(), true);
	}

	private String saveImage(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			return null;
		}

		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Only image files are allowed");
		}

		if (file.getSize() > MAX_PHOTO_SIZE) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Image size cannot exceed 10 MB");
		}

		return fileService.saveFile(file);
	}
}

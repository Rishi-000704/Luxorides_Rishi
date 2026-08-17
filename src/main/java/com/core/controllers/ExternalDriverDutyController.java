package com.core.controllers;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.DriverDutyEndRequest;
import com.core.dtos.driverduty.DriverDutyEndResponse;
import com.core.dtos.driverduty.DriverDutyStartRequest;
import com.core.dtos.driverduty.DriverDutyStartResponse;
import com.core.dtos.driverduty.DriverDutySummaryResponse;
import com.core.gateway.razerpay.QrPaymentStatusResponse;
import com.core.services.ExternalDriverDutyService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/driver-api/duty")
@CrossOrigin("*")
@RequiredArgsConstructor
public class ExternalDriverDutyController {

	private final ExternalDriverDutyService externalDriverDutyService;

	@GetMapping("/{token}")
	public DriverDutySummaryResponse getDutySummary(@PathVariable String token) {
		return externalDriverDutyService.getDutySummary(token);
	}

	@GetMapping("/{token}/payment-status")
	public QrPaymentStatusResponse checkQrPaymentStatus(@PathVariable String token) {
		return externalDriverDutyService.checkQrPaymentStatus(token);
	}

	@PostMapping(
			value = "/{token}/start",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	public DriverDutyStartResponse submitStart(
			@PathVariable String token,
			@RequestPart("payload") DriverDutyStartRequest payload,
			@RequestPart("odometerPhoto") MultipartFile odometerPhoto,
			HttpServletRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) throws IOException {
		return externalDriverDutyService.submitStart(
				token,
				payload,
				odometerPhoto,
				getClientIp(request),
				userAgent
		);
	}

	@PostMapping(
			value = "/{token}/end",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	public DriverDutyEndResponse submitEnd(
			@PathVariable String token,
			@RequestPart("payload") DriverDutyEndRequest payload,
			@RequestPart("odometerPhoto") MultipartFile odometerPhoto,
			@RequestPart(value = "receiptPhotos", required = false) List<MultipartFile> receiptPhotos,
			HttpServletRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) throws IOException {
		return externalDriverDutyService.submitEnd(
				token,
				payload,
				odometerPhoto,
				receiptPhotos,
				getClientIp(request),
				userAgent
		);
	}

	private String getClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");

		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}

		return request.getRemoteAddr();
	}
}
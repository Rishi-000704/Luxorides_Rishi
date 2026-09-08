package com.core.controllers;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.CashPaymentConfirmationResponse;
import com.core.dtos.driverduty.CloseDutyConfirmationResponse;
import com.core.dtos.driverduty.DriverDutyEndRequest;
import com.core.dtos.driverduty.DriverDutyEndResponse;
import com.core.dtos.driverduty.DriverDutyIncidentRequest;
import com.core.dtos.driverduty.DriverDutyIncidentResponse;
import com.core.dtos.driverduty.DriverDutyLocationPingRequest;
import com.core.dtos.driverduty.DriverDutyLocationResponse;
import com.core.dtos.driverduty.DriverDutyReturnGarageRequest;
import com.core.dtos.driverduty.DriverDutySosRequest;
import com.core.dtos.driverduty.DriverDutySosResponse;
import com.core.dtos.driverduty.DriverDutyStartRequest;
import com.core.dtos.driverduty.DriverDutyStartResponse;
import com.core.dtos.driverduty.DriverDutySummaryResponse;
import com.core.dtos.driverduty.DutyRouteLegResponse;
import com.core.dtos.driverduty.GarageReturnConfirmationResponse;
import com.core.dtos.driverduty.PickupOtpGenerateResponse;
import com.core.dtos.driverduty.PickupOtpVerifyRequest;
import com.core.dtos.driverduty.PickupOtpVerifyResponse;
import com.core.gateway.razerpay.QrPaymentStatusResponse;
import com.core.services.DriverDutyIncidentService;
import com.core.services.DriverDutySosService;
import com.core.services.ExternalDriverDutyService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/driver-api/duty")
@CrossOrigin("*")
@RequiredArgsConstructor
public class ExternalDriverDutyController {

	private final ExternalDriverDutyService externalDriverDutyService;
	private final DriverDutySosService driverDutySosService;
	private final DriverDutyIncidentService driverDutyIncidentService;

	@GetMapping("/{token}")
	public DriverDutySummaryResponse getDutySummary(@PathVariable String token) {
		return externalDriverDutyService.getDutySummary(token);
	}

	@GetMapping("/{token}/payment-status")
	public QrPaymentStatusResponse checkQrPaymentStatus(@PathVariable String token) {
		return externalDriverDutyService.checkQrPaymentStatus(token);
	}

	/*
	 * No request body -- the client submits nothing for this endpoint to
	 * trust. The authoritative outstanding amount is derived entirely
	 * server-side from the duty's own booking/payment state.
	 */
	@PostMapping("/{token}/cash/confirm")
	public CashPaymentConfirmationResponse confirmCashPayment(
			@PathVariable String token,
			HttpServletRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) {
		return externalDriverDutyService.confirmCashPayment(token, getClientIp(request), userAgent);
	}

	@GetMapping("/{token}/route/{leg}")
	public DutyRouteLegResponse getRouteForLeg(@PathVariable String token, @PathVariable String leg) {
		return externalDriverDutyService.getRouteForLeg(token, leg);
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

	@PostMapping("/{token}/pickup-otp/generate")
	public PickupOtpGenerateResponse generatePickupOtp(@PathVariable String token) {
		return externalDriverDutyService.generatePickupOtp(token);
	}

	@PostMapping("/{token}/pickup-otp/verify")
	public PickupOtpVerifyResponse verifyPickupOtp(
			@PathVariable String token,
			@RequestBody PickupOtpVerifyRequest payload
	) {
		return externalDriverDutyService.verifyPickupOtp(token, payload);
	}

	@PostMapping("/{token}/return-garage")
	public GarageReturnConfirmationResponse confirmGarageReturn(
			@PathVariable String token,
			@RequestBody(required = false) DriverDutyReturnGarageRequest payload,
			HttpServletRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) {
		return externalDriverDutyService.confirmGarageReturn(token, payload, getClientIp(request), userAgent);
	}

	@PostMapping("/{token}/close")
	public CloseDutyConfirmationResponse closeDuty(
			@PathVariable String token,
			HttpServletRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) {
		return externalDriverDutyService.closeDutyFromDriverApp(token, getClientIp(request), userAgent);
	}

	@PostMapping("/{token}/location")
	public DriverDutyLocationResponse submitLocationPing(
			@PathVariable String token,
			@RequestBody DriverDutyLocationPingRequest payload
	) {
		return externalDriverDutyService.submitLocationPing(token, payload);
	}

	@PostMapping("/{token}/sos")
	public DriverDutySosResponse submitSos(
			@PathVariable String token,
			@RequestBody DriverDutySosRequest payload,
			HttpServletRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) {
		return driverDutySosService.submitSos(token, payload, getClientIp(request), userAgent);
	}

	@PostMapping(
			value = "/{token}/incident",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	public DriverDutyIncidentResponse submitIncident(
			@PathVariable String token,
			@RequestPart("payload") DriverDutyIncidentRequest payload,
			@RequestPart(value = "photos", required = false) List<MultipartFile> photos,
			HttpServletRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) throws IOException {
		return driverDutyIncidentService.submitIncident(token, payload, photos, getClientIp(request), userAgent);
	}

	private String getClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");

		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}

		return request.getRemoteAddr();
	}
}
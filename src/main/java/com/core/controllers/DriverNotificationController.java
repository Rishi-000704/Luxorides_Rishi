package com.core.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.client.app.DeviceTokenRequest;
import com.core.dtos.client.app.NotificationSummaryResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.DriverAppService;

import lombok.RequiredArgsConstructor;

/*
 * Driver-authenticated notification feed + device-token registration.
 * Mirrors client.app.NotificationController exactly (same underlying
 * NotificationService, same generic NotificationSummaryResponse), but every
 * recipient id here is always resolved from the authenticated driver's own
 * record (see DriverAppService), never taken from the request.
 */
@RestController
@RequestMapping("/driver/app/notifications")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverNotificationController {

	private final SecurityContextUtil security;
	private final DriverAppService driverAppService;

	@GetMapping
	public NotificationSummaryResponse list() {
		return driverAppService.getNotifications(security.orgId(), security.userId());
	}

	@PostMapping("/{id}/read")
	public void markRead(@PathVariable String id) {
		driverAppService.markNotificationRead(security.orgId(), security.userId(), id);
	}

	@PostMapping("/device-token")
	public void registerDeviceToken(@RequestBody DeviceTokenRequest request) {
		driverAppService.registerDeviceToken(security.orgId(), security.userId(), request.token(), request.platform());
	}
}

package com.core.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.client.app.DeviceTokenRequest;
import com.core.security.SecurityContextUtil;
import com.core.services.DriverAppService;

import lombok.RequiredArgsConstructor;

/*
 * Driver-authenticated device-token registration for duty-assignment push
 * notifications. Reuses DeviceTokenRequest (token + platform) as-is -- the
 * shape is already recipient-agnostic, so a duplicate DTO isn't warranted.
 * Mirrors client.app.NotificationController's device-token endpoint, but
 * the recipient id is always resolved from the authenticated driver's own
 * record (see DriverAppService#registerDeviceToken) rather than taken from
 * the request body.
 */
@RestController
@RequestMapping("/driver/app/notifications")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverNotificationController {

	private final SecurityContextUtil security;
	private final DriverAppService driverAppService;

	@PostMapping("/device-token")
	public void registerDeviceToken(@RequestBody DeviceTokenRequest request) {
		driverAppService.registerDeviceToken(security.orgId(), security.userId(), request.token(), request.platform());
	}
}

package com.core.controllers.client.app;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.client.app.DeviceTokenRequest;
import com.core.dtos.client.app.NotificationSummaryResponse;
import com.core.models.Client;
import com.core.models.enums.NotificationRecipientType;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;
import com.core.services.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/client/app/notifications")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class NotificationController {

	private final SecurityContextUtil security;
	private final ClientService clientService;
	private final NotificationService notificationService;

	@GetMapping
	public NotificationSummaryResponse list() {
		Client client = clientService.findByUserId(security.userId());
		return notificationService.list(security.orgId(), NotificationRecipientType.CLIENT, client.getId());
	}

	@PostMapping("/{id}/read")
	public void markRead(@PathVariable String id) {
		Client client = clientService.findByUserId(security.userId());
		notificationService.markRead(id, security.orgId(), NotificationRecipientType.CLIENT, client.getId());
	}

	@PostMapping("/device-token")
	public void registerDeviceToken(@RequestBody DeviceTokenRequest request) {
		Client client = clientService.findByUserId(security.userId());
		notificationService.registerDeviceToken(
				security.orgId(), NotificationRecipientType.CLIENT, client.getId(),
				request.token(), request.platform());
	}
}

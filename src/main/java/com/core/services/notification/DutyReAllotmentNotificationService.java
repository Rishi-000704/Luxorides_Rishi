package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.services.common.EmailTemplateService;
import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;
import com.core.events.DutyReAllottedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyReAllotmentNotificationService {
	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(DutyReAllottedEvent event) {
		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {
				String html = templateService.render("email/duty-re-allotment", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Duty Re-Allotted", html);
			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}

		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {
				smsService.sendReDutyAllotmentSms(event.orgId(),event.recipientMobileNumber(), event.dutyId(), event.driverName(),
						event.driverPhone(), event.vehicleName(), event.vehicleNumber());
			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
	}
}

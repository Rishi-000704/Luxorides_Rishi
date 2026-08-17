package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;
import com.core.services.common.EmailTemplateService;
import com.core.events.DutyAllottedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyAllotmentNotificationService {

	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(DutyAllottedEvent event) {
		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {
				String emailHtml = templateService.render("email/duty-allotment", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Duty Allotted", emailHtml);
			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}

		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {
				smsService.sendDutyAllotmentSms(event.orgId(),event.recipientMobileNumber(), event.dutyId(), event.driverName(),
						event.driverPhone(), event.vehicleName(), event.vehicleNumber());
			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
	}
}

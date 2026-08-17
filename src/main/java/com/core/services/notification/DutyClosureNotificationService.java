package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;
import com.core.services.common.EmailTemplateService;
import com.core.events.DutyClosedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyClosureNotificationService {

	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(DutyClosedEvent event) {
		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {
				String emailHtml = templateService.render("email/duty-closure", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Trip Summary", emailHtml);
			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {
				smsService.sendDutyClosureSms(event.orgId(),event.recipientMobileNumber(), event.dutyId());
			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
	}
}

package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.services.common.EmailTemplateService;
import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;
import com.core.events.DutyReClosedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyReClosureNotificationService {

	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(DutyReClosedEvent event) {
		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {
				String html = templateService.render("email/duty-re-closure", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Duty Re-Closed", html);
			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {
				smsService.sendReDutyClosureSms(event.orgId(),event.recipientMobileNumber(), event.bookingId());
			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
	}
}

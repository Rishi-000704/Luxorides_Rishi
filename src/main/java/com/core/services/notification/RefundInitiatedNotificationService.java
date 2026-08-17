package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.events.RefundInitiatedEvent;
import com.core.services.common.EmailTemplateService;
import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefundInitiatedNotificationService {

	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(RefundInitiatedEvent event) {
		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {
				String html = templateService.render("email/refund-initiated", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Refund Initiated", html);
			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {
				smsService.sendRefundInitiatedSms(event.orgId(),event.recipientMobileNumber(), event.bookingId(),
						event.refundAmount());
			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}

	}
}

package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.services.common.EmailTemplateService;
import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;
import com.core.events.PaymentPendingEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentPendingNotificationService {

	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(PaymentPendingEvent event) {
		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {
				String html = templateService.render("email/payment-pending", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Payment Pending", html);
			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}

		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {
				smsService.sendPaymentPendingSms(event.orgId(),event.recipientMobileNumber(), event.bookingId(),
						event.pendingAmount());
			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
	}
}

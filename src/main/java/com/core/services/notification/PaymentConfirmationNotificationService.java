package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;
import com.core.services.common.EmailTemplateService;
import com.core.events.PaymentConfirmedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentConfirmationNotificationService {

	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(PaymentConfirmedEvent event) {
		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {
				String emailHtml = templateService.render("email/payment-confirmation", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Payment Received", emailHtml);
			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}

		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {
				smsService.sendPaymentConfirmationSms(event.orgId(),event.recipientMobileNumber(), event.bookingId(),
						event.amountPaid());
			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
	}
}

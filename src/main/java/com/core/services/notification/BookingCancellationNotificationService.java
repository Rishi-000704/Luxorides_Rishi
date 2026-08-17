package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.events.BookingCancelledEvent;
import com.core.services.common.EmailTemplateService;
import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingCancellationNotificationService {
	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(BookingCancelledEvent event) {
		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {
				String html = templateService.render("email/booking-cancellation", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Booking Cancelled", html);
			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}

		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {
				smsService.sendBookingCancellationSms(event.orgId(),event.recipientMobileNumber(), event.bookingId());
			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
	}
}

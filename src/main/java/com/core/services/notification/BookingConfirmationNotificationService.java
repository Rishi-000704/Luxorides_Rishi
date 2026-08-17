package com.core.services.notification;

import org.springframework.stereotype.Component;

import com.core.events.BookingConfirmedEvent;
import com.core.services.common.EmailTemplateService;
import com.core.services.common.SMSService;
import com.core.services.common.ZohoMailService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingConfirmationNotificationService {

	private final EmailTemplateService templateService;
	private final ZohoMailService zohoMailService;
	private final SMSService smsService;

	public void notify(BookingConfirmedEvent event) {

		if (event.recipientEmail() != null && !event.recipientEmail().isEmpty()) {
			try {

				String emailHtml = templateService.render("email/booking-confirmation", "data", event);

				zohoMailService.send(event.orgId(),event.recipientEmail(), "Booking Confirmed | Luxorides | " + event.bookingId(),
						emailHtml);

			} catch (Exception ex) {
				System.err.println("Email notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
		if (event.recipientMobileNumber() != null && !event.recipientMobileNumber().isEmpty()) {
			try {

				smsService.sendBookingConfirmation(event.orgId(),event.recipientMobileNumber(), event.bookingId(),
						String.valueOf(event.totalDuties()));

			} catch (Exception ex) {
				System.err.println("SMS notification failed for booking " + event.bookingId());
				System.err.println(ex.getMessage());
			}
		}
	}
}

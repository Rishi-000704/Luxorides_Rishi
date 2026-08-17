package com.core.events.assembler;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.core.events.RefundCompletedEvent;
import com.core.events.RefundInitiatedEvent;
import com.core.models.Booking;
import com.core.models.Payment;
import com.core.models.enums.PaymentStatus;

@Component
public class RefundEventAssembler {

	public RefundInitiatedEvent toRefundInitiatedEvent(Booking booking) {
		BigDecimal paidAmount = BigDecimal.ZERO;
		for (Payment p : booking.getPayments()) {
			if (p.getStatus().equals(PaymentStatus.CONFIRMED)) {
				paidAmount = paidAmount.add(p.getReceivedAmount().getAmount().add(p.getTds().getAmount()));
			}
		}
		return new RefundInitiatedEvent(
				booking.getOrgId(),
				booking.getClient().getEmail(), booking.getClient().getPhone(),
				booking.getClient().getName().getDisplayName(),

				booking.getBookingId(),

				paidAmount.toString());
	}

	public RefundCompletedEvent toRefundCompletedEvent(Booking booking) {
		BigDecimal paidAmount = BigDecimal.ZERO;
		for (Payment p : booking.getPayments()) {
			if (p.getStatus().equals(PaymentStatus.REFUNDED)) {
				paidAmount = paidAmount.add(p.getReceivedAmount().getAmount().add(p.getTds().getAmount()));
			}
		}
		return new RefundCompletedEvent(
				booking.getOrgId(),
				booking.getClient().getEmail(), booking.getClient().getPhone(),
				booking.getClient().getName().getDisplayName(),

				booking.getBookingId(),

				paidAmount.toString());
	}
}

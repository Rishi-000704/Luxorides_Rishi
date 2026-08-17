package com.core.events.assembler;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.core.events.PaymentConfirmedEvent;
import com.core.events.PaymentPendingEvent;
import com.core.models.Booking;
import com.core.models.Client;
import com.core.models.Payment;
import com.core.models.enums.PaymentStatus;
import com.core.util.DateFormatUtil;

@Component
public class PaymentEventAssembler {

	public PaymentConfirmedEvent
	toPaymentConfirmedEvent(

			Booking booking,

			Payment payment) {

		Client client =
				booking.getClient();

		return new PaymentConfirmedEvent(

				booking.getOrgId(),

				client == null
						? null
						: client.getEmail(),

				client == null
						? null
						: client.getPhone(),

				client == null
						|| client.getName() == null
						? "Client"
						: client.getName()
						.getDisplayName(),

				booking.getBookingId(),

				payment.getTransactionNumber(),

				payment
						.getReceivedAmount()
						.getAmount()
						.toString(),

				DateFormatUtil.display(
						payment.getTransactionDate()),

				payment
						.getPaymentMode()
						.name());
	}

	public PaymentPendingEvent
	toPaymentPendingEvent(
			Booking booking) {

		BigDecimal paidAmount =
				BigDecimal.ZERO;

		for (Payment payment
				: booking.getPayments()) {

			if (payment.getStatus()
					== PaymentStatus.CONFIRMED) {

				paidAmount =
						paidAmount.add(

								payment
										.getReceivedAmount()
										.getAmount()

										.add(
												payment
														.getTds()
														.getAmount()));
			}
		}

		BigDecimal pendingAmount =
				booking
						.getTotal()
						.getAmount()
						.subtract(
								paidAmount);

		Client client =
				booking.getClient();

		return new PaymentPendingEvent(

				booking.getOrgId(),

				client == null
						? null
						: client.getEmail(),

				client == null
						? null
						: client.getPhone(),

				client == null
						|| client.getName() == null
						? "Client"
						: client.getName()
						.getDisplayName(),

				booking.getBookingId(),

				booking
						.getTotal()
						.getAmount()
						.toString(),

				paidAmount.toString(),

				pendingAmount.toString());
	}
}
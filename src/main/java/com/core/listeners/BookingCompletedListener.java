package com.core.listeners;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.core.events.BookingCompletedEvent;
import com.core.services.InvoiceService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingCompletedListener {

	private final InvoiceService invoiceService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onBookingCompleted(BookingCompletedEvent event) {

		invoiceService.createInvoice(
				event.bookingId(),
				event.orgId()
		);
	}
}
package com.core.listeners;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.core.events.SyncInvoiceEvent;
import com.core.services.InvoiceService;

import lombok.RequiredArgsConstructor;
@Component
@RequiredArgsConstructor
public class SyncInvoiceListener {
	private final InvoiceService invoiceService;

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void onBookingUpdated(SyncInvoiceEvent event) {
		invoiceService.syncInvoiceFromBooking(
				event.bookingId(),
				event.invoiceNumber(),
				event.orgId()
		);
	}
}

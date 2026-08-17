package com.core.events;

public record SyncInvoiceEvent(String bookingId, String invoiceNumber, String orgId) {

}

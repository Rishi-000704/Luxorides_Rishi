package com.core.dtos.booking;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.core.dtos.common.MoneyDTO;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.enums.BookingStatus;

public record BookingListItem(
		String bookingId,
		String clientName,
		String phone,
		String billingEntity,
		Integer duties,
		Instant createdAt,
		Instant updatedAt,
		Instant firstDutyReportingTime,
		List<BookingDutyReportingDate> dutyReportingDates,
		BookingStatus status,
		MoneyDTO total
) {

	public static BookingListItem from(Booking b) {
		String billingEntityName = b.getClientBillingEntity() != null
				? b.getClientBillingEntity().getLegalName()
				: null;

		List<BookingEntry> entries = b.getEntries() == null
				? List.of()
				: b.getEntries();

		List<BookingDutyReportingDate> reportingDates = entries.stream()
				.sorted(Comparator.comparing(
						BookingEntry::getReportingTime,
						Comparator.nullsLast(Comparator.naturalOrder())
				))
				.map(e -> new BookingDutyReportingDate(
						e.getDutyId(),
						e.getReportingTime(),
						e.getDropTime(),
						e.getStatus()
				))
				.toList();

		Instant firstDutyReportingTime = entries.stream()
				.map(BookingEntry::getReportingTime)
				.filter(Objects::nonNull)
				.min(Instant::compareTo)
				.orElse(null);

		return new BookingListItem(
				b.getBookingId(),
				b.getClient().getName().getDisplayName(),
				b.getClient().getPhone(),
				billingEntityName,
				entries.size(),
				b.getCreatedAt(),
				b.getUpdatedAt(),
				firstDutyReportingTime,
				reportingDates,
				b.getStatus(),
				new MoneyDTO(b.getTotal().getAmount(), b.getTotal().getCurrency())
		);
	}
}
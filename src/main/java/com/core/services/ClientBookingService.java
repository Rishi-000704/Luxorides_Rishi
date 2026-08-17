package com.core.services;

import java.math.BigDecimal;
import java.util.List;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.booking.BookingDTO;
import com.core.dtos.booking.BookingForm;
import com.core.dtos.booking.DutyForm;
import com.core.dtos.client.app.ClientBookingDraftDTO;
import com.core.models.Booking;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.Currency;
import com.core.models.enums.GstType;
import com.core.models.enums.PaymentStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientBookingService {

	private final BookingService bookingService;

	@PersistenceContext
	private EntityManager entityManager;

	/* ================= DRAFT BOOKING ================= */

	@Transactional
	public Booking draftBooking(ClientBookingDraftDTO form, String loggedInClientId, String orgId) {

		BookingForm bform = new BookingForm(null, loggedInClientId, form.clientBillingEntityId(), "", GstType.IGST,
				form.clientBillingEntityId() != null ? 18 : 5, BigDecimal.ZERO);
		BookingDTO booking = bookingService.addBooking(bform, orgId);

		for (ClientBookingDraftDTO.Entry entry : form.entries()) {
			DutyForm dform = new DutyForm(booking.bookingId(), null, entry.passengerIds(), entry.reportingTime(),
					entry.reportingLocation(), entry.dropLocation(), entry.dropTime(), entry.vehicleId(),
					entry.flightNumber(), entry.packageId(), entry.clientNotes());
			bookingService.addBookingEntry(dform, orgId);
		}
		entityManager.flush();
		entityManager.clear();
		return this.getBooking(booking.bookingId(), orgId);
	}

	/* ================= CONFIRM (NON-GATEWAY PAYMENTS) ================= */

	@Transactional
	public Booking confirmBooking(String orgId, String bookingId) {

		Booking booking = bookingService.getBooking(bookingId, orgId);

		/* ================= STATUS ================= */

		if (booking.getStatus() != BookingStatus.DRAFT) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Booking already processed");
		}

		/* ================= FINANCIAL VALIDATION ================= */

		Money bookingTotal = booking.getTotal();
		if (bookingTotal == null || bookingTotal.getAmount() == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Booking total is not calculated");
		}

		Currency currency = bookingTotal.getCurrency();

		// Sum confirmed payments
		BigDecimal paidAmount = booking.getPayments().stream().filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
				.map(Payment::getReceivedAmount).peek(m -> {
					if (!currency.equals(m.getCurrency())) {
						throw new BusinessException(ErrorCode.BAD_REQUEST, "Payment currency mismatch. Expected: " + currency + ", Found: " + m.getCurrency());
					}
				}).map(Money::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal bookingAmount = bookingTotal.getAmount();

		if (paidAmount.compareTo(bookingAmount) < 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Insufficient payment. Paid: " + paidAmount + ", Required: " + bookingAmount);
		}

		if (paidAmount.compareTo(bookingAmount) > 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Overpayment detected. Paid: " + paidAmount + ", Required: " + bookingAmount);
		}

		/* ================= CONFIRM ================= */

		bookingService.confirmBooking(bookingId, orgId);

		return this.getBooking(bookingId, orgId);
	}

	/* ================= READ APIs ================= */

	@Transactional(readOnly = true)
	public List<Booking> getMyBookings(String clientId, String orgId) {
		return bookingService.getClientBookings(clientId, orgId);
	}

	@Transactional(readOnly = true)
	public Booking getBooking(String bookingId, String orgId) {
		return bookingService.getBooking(bookingId, orgId);
	}
}

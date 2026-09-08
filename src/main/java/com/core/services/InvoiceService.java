package com.core.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import com.core.exception.NotFoundException;
import com.core.models.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.common.MoneyDTO;
import com.core.dtos.common.PdfStream;
import com.core.dtos.invoice.ClientPendingListItem;
import com.core.dtos.invoice.InvoiceListItem;
import com.core.dtos.invoice.InvoicePendingListItem;
import com.core.events.BookingBilledEvent;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.location.orchestrator.GeoProviderChain;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.ExtraCharge;
import com.core.models.FinancialYear;
import com.core.models.Invoice;
import com.core.models.InvoiceEntry;
import com.core.models.InvoiceExtraCharge;
import com.core.models.OrgBillingEntity;
import com.core.models.Payment;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.repositories.BookingRepository;
import com.core.repositories.FinancialYearRepository;
import com.core.repositories.InvoiceRepository;
import com.core.repositories.OrgBillingEntityRepository;
import com.core.services.common.PdfService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
public class InvoiceService {

	private final InvoiceRepository invoiceRepository;
	private final BookingRepository bookingRepository;
	private final FinancialYearRepository financialYearRepository;
	private final OrgBillingEntityRepository orgBillingEntityRepository;
	private final ClientService clientService;
	private final PdfService pdfService;
	private final GeoProviderChain geoProviderChain;
	private final ApplicationEventPublisher eventPublisher;

	/* =============== INVOICE LIST ==================== */

	public Page<InvoiceListItem> getPage(String orgId, InvoiceStatus status, String searchstr, Pageable pageable) {

		Page<Invoice> invoices = invoiceRepository.searchInvoicesByStatus(orgId, status, searchstr, pageable);

		return invoices.map(this::toListItem);
	}

	private InvoiceListItem toListItem(Invoice invoice) {

		return new InvoiceListItem(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
				invoice.getClient().getName().getDisplayName(),
				invoice.getClientBillingEntity() != null ? invoice.getClientBillingEntity().getLegalName() : "Walk-in",
				new MoneyDTO(invoice.getGrandTotal().getAmount(), invoice.getGrandTotal().getCurrency()),
				new MoneyDTO(invoice.getTotalPaid().getAmount(), invoice.getTotalPaid().getCurrency()),
				new MoneyDTO(invoice.getBalanceAmount().getAmount(), invoice.getBalanceAmount().getCurrency()),
				invoice.getStatus());
	}

	@Transactional(readOnly = true)
	public Page<InvoiceListItem> getClientInvoicePage(
			String orgId,
			String clientId,
			InvoiceStatus status,
			String search,
			Pageable pageable
	) {
		return invoiceRepository
				.searchClientInvoices(orgId, clientId, status, search, pageable)
				.map(this::toListItem);
	}

	/* =============== PENDING LIST ==================== */

	public Page<InvoicePendingListItem> getInvoicePendingList(String orgId, String searchstr, Pageable pageable) {
		Page<Invoice> invoices = invoiceRepository.searchInvoicesByStatus(orgId, InvoiceStatus.ISSUED, searchstr,
				pageable);
		return invoices.map(this::toInvoicePendingListItem);
	}

	private InvoicePendingListItem toInvoicePendingListItem(Invoice invoice) {
		return new InvoicePendingListItem(invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
				invoice.getClient().getName().getDisplayName(), invoice.getClient().getPhone(),
				invoice.getClientBillingEntity() != null ? invoice.getClientBillingEntity().getLegalName() : "Walk-in",
				invoice.getClientBillingEntity() != null ? invoice.getClientBillingEntity().getPhone() : "-",
				invoice.getBalanceAmount().getAmount());
	}

	public Page<ClientPendingListItem> getClientPendingList(String orgId, String searchstr, Pageable pageable) {

		return invoiceRepository.getClientPendingPage(orgId, searchstr, pageable);
	}

	/* =============== CREATE INVOICE (ONE TIME) ==================== */

	@Transactional
	public void createInvoice(String bookingId, String orgId) {

		Booking booking = bookingRepository.lockByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));

		if (booking.getStatus() == BookingStatus.BILLED) {
			throw new BusinessException(
					ErrorCode.BOOKING_ALREADY_BILLED,
					"Booking is already marked as billed"
			);
		}

		if (booking.getStatus() != BookingStatus.COMPLETED) {
			throw new BusinessException(
					ErrorCode.BOOKING_NOT_COMPLETED,
					"Invoice can be generated only after booking is completed. Current status: " + booking.getStatus()
			);
		}

		if (!allDutiesCompleted(booking)) {
			throw new BusinessException(
					ErrorCode.DUTIES_NOT_COMPLETED,
					"Invoice can be generated only after all duties are completed."
			);
		}

		Invoice existingByBooking = invoiceRepository.findByBookingIdAndOrgId(bookingId, orgId).orElse(null);

		if (existingByBooking != null) {
			throw new BusinessException(
					ErrorCode.BOOKING_ALREADY_BILLED,
					"Booking already has invoice"
			);
		}

		if (hasText(booking.getInvoiceNumber())) {
			invoiceRepository
					.findInvoiceByInvoiceNumberAndOrgId(booking.getInvoiceNumber(), orgId)
					.orElseThrow(() -> new BusinessException(
							ErrorCode.BOOKING_ALREADY_BILLED,
							"Booking is marked billed, but invoice record was not found"
					));

			throw new BusinessException(
					ErrorCode.BOOKING_ALREADY_BILLED,
					"Booking already has invoice number"
			);
		}

		Invoice invoice = new Invoice();
		invoice.setOrgId(orgId);
		invoice.setBookingId(bookingId);
		invoice.setInvoiceDate(Instant.now());
		invoice.setStatus(InvoiceStatus.ISSUED);

		OrgBillingEntity orgBE = resolveOrgBillingEntity(booking, orgId);
		invoice.setOrgBillingEntityId(orgBE.getId());
		invoice.setInvoiceNumber(generateInvoiceNumber(orgId, orgBE.getId()));

		initialiseCollections(invoice);
		syncInvoiceInternal(booking, invoice, orgId);

		invoiceRepository.save(invoice);

		booking.setStatus(BookingStatus.BILLED);
		booking.setInvoiceNumber(invoice.getInvoiceNumber());
		bookingRepository.save(booking);

		eventPublisher.publishEvent(new BookingBilledEvent(bookingId, orgId));
	}

	/* ============== SYNC / RELOAD (SAFE, REPEATABLE) ============== */

	@Transactional
	public void syncInvoiceFromBooking(String bookingId, String invoiceNumber, String orgId) {

		Booking booking = bookingRepository.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));

		Invoice invoice = invoiceRepository.findInvoiceByInvoiceNumberAndOrgId(invoiceNumber, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));

		initialiseCollections(invoice);
		syncInvoiceInternal(booking, invoice, orgId);
	}

	/* ============ CORE SYNC LOGIC (ACCOUNTING SAFE) ============== */

	private void syncInvoiceInternal(Booking booking, Invoice invoice, String orgId) {

		boolean cancelledInvoice = booking.getStatus() == BookingStatus.CANCELLED;

		/* ---------- HEADER ---------- */

		invoice.setClientId(booking.getClientId());
		invoice.setClientBillingEntityId(booking.getClientBillingEntityId());
		invoice.setRemarks(booking.getRemarks());

		/* ---------- ENTRIES ---------- */

		Map<String, InvoiceEntry> existing = invoice.getEntries().stream().filter(e -> e.getDutyId() != null)
				.collect(Collectors.toMap(InvoiceEntry::getDutyId, e -> e));

		invoice.getEntries().clear();

		BigDecimal subTotal = BigDecimal.ZERO;

		for (BookingEntry be : booking.getEntries()) {

			InvoiceEntry ie = existing.getOrDefault(be.getDutyId(), new InvoiceEntry());
			ie.setInvoice(invoice);

			mapBookingEntryToInvoiceEntry(be, ie);
			syncCharges(be, ie);
			syncPassengers(be, ie);

			invoice.getEntries().add(ie);

			BigDecimal dutyAmount = be.getDutyTotal().getAmount();
			if (dutyAmount == null) {
				throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty total missing for entry: " + be.getDutyId());
			}

			subTotal = subTotal.add(dutyAmount);
		}

		/* 🔒 ROUND TAXABLE BASE */
		subTotal = subTotal.setScale(0, RoundingMode.HALF_UP);

		BigDecimal discountAmount = BigDecimal.ZERO;

		if (booking.getDiscount() != null && booking.getDiscount().getAmount() != null) {
			discountAmount = booking.getDiscount().getAmount();
		}

		if (discountAmount.signum() < 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Discount cannot be negative");
		}

		if (discountAmount.compareTo(subTotal) > 0) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Discount cannot be greater than invoice subtotal");
		}

		BigDecimal taxableAmount = subTotal.subtract(discountAmount).setScale(0, RoundingMode.HALF_UP);

		/* ---------- TAX ---------- */

		OrgBillingEntity orgBE = resolveOrgBillingEntity(booking, orgId);
		invoice.setOrgBillingEntityId(orgBE.getId());

		String placeOfSupply = resolvePlaceOfSupply(booking);
		invoice.setPlaceOfSupply(placeOfSupply);

		String orgState = normalize(orgBE.getAddress().getState());

		GstType gstType = orgState.toLowerCase().equals(placeOfSupply.toLowerCase()) ? GstType.CGST_SGST // ✅ same state
				: GstType.IGST; // ✅ different state

		GstSnapshot gst = GstSnapshot.of(gstType, taxableAmount, booking.getGstSnapshot().getGstRate());

		invoice.setSubtotal(Money.INR(subTotal));
		invoice.setDiscount(Money.INR(discountAmount));
		invoice.setTaxableAmount(Money.INR(taxableAmount));
		invoice.setGstSnapshot(gst);

		BigDecimal grandTotal = taxableAmount.add(gst.getTotalTax()).setScale(0, RoundingMode.HALF_UP);

		invoice.setGrandTotal(Money.INR(grandTotal));

		/* ---------- PAYMENTS ---------- */

		BigDecimal paid = BigDecimal.ZERO;

		for (Payment p : booking.getPayments()) {
			p.setInvoice(invoice);

			if (p.getStatus() == PaymentStatus.CONFIRMED) {

				BigDecimal received = p.getReceivedAmount() != null ? p.getReceivedAmount().getAmount()
						: BigDecimal.ZERO;

				BigDecimal tds = p.getTds() != null ? p.getTds().getAmount() : BigDecimal.ZERO;

				BigDecimal paymentTotal = received.add(tds).setScale(0, RoundingMode.HALF_UP);

				paid = paid.add(paymentTotal);
			}
		}

		BigDecimal pending = grandTotal.subtract(paid).setScale(0, RoundingMode.HALF_UP);

		invoice.setTotalPaid(Money.INR(paid));

		invoice.setBalanceAmount(
				pending.compareTo(BigDecimal.ZERO) > 0 ? Money.INR(pending) : Money.INR(BigDecimal.ZERO));

		if (!cancelledInvoice) {
			invoice.setStatus(pending.signum() <= 0 ? InvoiceStatus.PAID : InvoiceStatus.ISSUED);
		}else{
			invoice.setStatus(InvoiceStatus.CANCELLED);
		}
	}

	/* ================= HELPERS ======================== */

	private void initialiseCollections(Invoice invoice) {
		if (invoice.getEntries() == null) {
			invoice.setEntries(new ArrayList<>());
		}
		if (invoice.getPayments() == null) {
			invoice.setPayments(new ArrayList<>());
		}
	}

	private void syncCharges(BookingEntry be, InvoiceEntry ie) {
		if (ie.getCharges() == null) {
			ie.setCharges(new ArrayList<>());
		} else {
			ie.getCharges().clear();
		}

		if (be.getCharges() != null) {
			for (ExtraCharge ec : be.getCharges()) {
				InvoiceExtraCharge c = new InvoiceExtraCharge();
				c.setInvoiceEntry(ie);
				c.setDescription(ec.getDescription());
				c.setImage(ec.getImage());
				c.setAmount(ec.getAmount());
				ie.getCharges().add(c);
			}
		}
	}

	private void syncPassengers(BookingEntry be, InvoiceEntry ie) {
		ie.setPassengerNames(be.getPassengerIds().stream()
				.map(pid -> clientService.getPassenger(pid).getName().getDisplayName()).toList());
	}

	private OrgBillingEntity resolveOrgBillingEntity(Booking booking, String orgId) {
		Integer gstRate = booking.getGstSnapshot().getGstRate();

		return orgBillingEntityRepository.findByGstRateAndOrgId(gstRate, orgId)
				.orElseGet(() -> orgBillingEntityRepository.findByOrgId(orgId).stream().findFirst()
						.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_ENTITY_NOT_FOUND,
								"Please provide org's billing entity first.")));
	}

	private String generateInvoiceNumber(String orgId, String billingEntityId) {
		FinancialYear fy = financialYearRepository.lockCurrentFinancialYear(orgId, billingEntityId);

		if (fy == null) {
			throw new BusinessException(
					ErrorCode.FINANCIAL_YEAR_NOT_FOUND,
					"Current financial year is not configured for selected billing entity"
			);
		}

		int nextCounter = fy.getInvoiceCounter() == null ? 1 : fy.getInvoiceCounter() + 1;

		fy.setInvoiceCounter(nextCounter);
		financialYearRepository.save(fy);

		return fy.getInvoicePrefix() + "-" + String.format("%06d", nextCounter);
	}

	/* ======================= PDF ========================= */

	@Transactional(readOnly = true)
	public PdfStream getInvoicePdf(String invoiceNumber, String orgId) {

		Invoice invoice = invoiceRepository.findInvoiceByInvoiceNumberAndOrgId(invoiceNumber, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));

		return pdfService.generateInvoicePdfStream(invoice);
	}

	/*
	 * P0 IDOR fix -- getInvoicePdf above is org-scoped only, which is correct
	 * for its other caller (EmployeeInvoiceController: an org employee may
	 * legitimately download any invoice in their org). The customer-facing
	 * download must additionally prove the requesting client owns the
	 * invoice, since invoice numbers are sequential and easily guessed.
	 */
	@Transactional(readOnly = true)
	public PdfStream getInvoicePdfForClient(String invoiceNumber, String clientId, String orgId) {

		Invoice invoice = invoiceRepository.findInvoiceByInvoiceNumberAndOrgId(invoiceNumber, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not found"));

		if (!clientId.equals(invoice.getClientId())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "This invoice does not belong to you");
		}

		return pdfService.generateInvoicePdfStream(invoice);
	}

	@Transactional
	public PdfStream reloadInvoicePdf(String bookingId, String orgId) {

		Booking booking = bookingRepository.findByBookingIdAndOrgId(bookingId, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));

		if (booking.getStatus() != BookingStatus.BILLED) {
			throw new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Invoice not present");
		}

		syncInvoiceFromBooking(bookingId, booking.getInvoiceNumber(), orgId);
		return getInvoicePdf(booking.getInvoiceNumber(), orgId);
	}

	/* ==================== ENTRY MAPPING ====================== */

	private void mapBookingEntryToInvoiceEntry(BookingEntry be, InvoiceEntry ie) {

		ie.setDutyId(be.getDutyId());
		ie.setPack(be.getPack());

		ie.setMasterVehicleId(be.getMasterVehicleId());
		ie.setSupplierId(be.getSupplierId());
		ie.setFleetVehicleId(be.getFleetVehicleId());
		ie.setDriverId(be.getDriverId());

		ie.setReportingTime(be.getReportingTime());
		ie.setReportingLocation(be.getReportingLocation());
		ie.setStartAt(be.getStartAt());

		ie.setDropTime(be.getDropTime());
		ie.setDropLocation(be.getDropLocation());
		ie.setEndAt(be.getEndAt());

		ie.setDutySlipImage(be.getDutySlipImage());

		ie.setStartingKM(be.getStartingKM());
		ie.setClosingKM(be.getClosingKM());

		ie.setRunningDays(be.getRunningDays());
		ie.setExtraChargebleDistance(be.getExtraChargebleDistance());
		ie.setExtraChargebleTime(be.getExtraChargebleTime());
		ie.setNightChargeble(be.getNightChargeble());

		ie.setFlightNumber(be.getFlightNumber());
		ie.setGarageLocation(be.getGarageLocation());
		ie.setClientNotes(be.getClientNotes());

		/* -------- Derived values -------- */

		if (be.getStartingKM() != null && be.getClosingKM() != null) {
			int km = be.getClosingKM() - be.getStartingKM();
			ie.setTotalRunningKM(Math.max(km, 0));
		} else {
			ie.setTotalRunningKM(null);
		}

		if (be.getStartAt() != null && be.getEndAt() != null) {
			long minutes = Duration.between(be.getStartAt(), be.getEndAt()).toMinutes();
			BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

			ie.setTotalRunningHS(hours.floatValue());
		} else {
			ie.setTotalRunningHS(null);
		}

		if (be.getExtraChargebleDistance() != null && be.getPack() != null) {
			BigDecimal extraKm = BigDecimal.valueOf(be.getExtraChargebleDistance())
					.multiply(be.getPack().getExtraPerKM().getAmount()).setScale(0, RoundingMode.HALF_UP);

			ie.setExtraChargeDistance(Money.INR(extraKm));
		} else {
			ie.setExtraChargeDistance(null);
		}

		if (be.getExtraChargebleTime() != null && be.getPack() != null) {
			BigDecimal extraTime = BigDecimal.valueOf(be.getExtraChargebleTime())
					.multiply(be.getPack().getExtraPerHS().getAmount()).setScale(0, RoundingMode.HALF_UP);

			ie.setExtraChargeTime(Money.INR(extraTime));
		} else {
			ie.setExtraChargeTime(null);
		}

		ie.setChargebleBaseFare(Money.INR(be.getPack().getDutyType().equals(DutyType.OUTSTATION)
				? be.getPack().getBaseFare().getAmount().multiply(BigDecimal.valueOf(be.getRunningDays()))
				: be.getPack().getBaseFare().getAmount()));

		ie.setDutyTotal(be.getDutyTotal());
	}

	private String resolvePlaceOfSupply(Booking booking) {

		// 1. Prefer client billing entity
		if (booking.getClientBillingEntity() != null && booking.getClientBillingEntity().getAddress() != null
				&& booking.getClientBillingEntity().getAddress().getState() != null) {

			return normalize(booking.getClientBillingEntity().getAddress().getState());
		}

		// 2. Fallback → reporting location
		if (booking.getEntries() != null && !booking.getEntries().isEmpty()) {

			AddressSnapshot reporting = booking.getEntries().get(0).getReportingLocation();

			return normalize(geoProviderChain.resolveState(reporting));
		}

		throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to determine Place of Supply");
	}

	private String normalize(String state) {
		return state.trim().toUpperCase();
	}

	private boolean allDutiesCompleted(Booking booking) {
		return booking.getEntries() != null
				&& !booking.getEntries().isEmpty()
				&& booking.getEntries()
				.stream()
				.allMatch(entry -> entry.getStatus() == DutyStatus.COMPLETED);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}

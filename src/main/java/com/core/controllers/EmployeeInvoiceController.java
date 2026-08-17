package com.core.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.core.dtos.common.PdfStream;
import com.core.dtos.invoice.ClientPendingListItem;
import com.core.dtos.invoice.InvoiceListItem;
import com.core.dtos.invoice.InvoicePendingListItem;
import com.core.models.enums.InvoiceStatus;
import com.core.security.SecurityContextUtil;
import com.core.services.InvoiceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/invoice")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeInvoiceController {

	private final InvoiceService invoiceService;
	private final SecurityContextUtil security;

	/* ===================== FETCH INVOICE ================= */

	@GetMapping("/page")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public Page<InvoiceListItem> getPage(@RequestParam(defaultValue = "") String searchstr,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(required = false) InvoiceStatus status,
			@RequestParam(defaultValue = "DESC") Sort.Direction direction) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
		return invoiceService.getPage(security.orgId(), status, searchstr.trim(), pageable);
	}

	@GetMapping("/pending/client/page")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public Page<ClientPendingListItem> getClientPendingPage(@RequestParam(defaultValue = "") String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {

		Pageable pageable = PageRequest.of(page, size);

		return invoiceService.getClientPendingList(security.orgId(), search.trim(), pageable);
	}

	@GetMapping("/pending/page")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public Page<InvoicePendingListItem> getInvoicePendingPage(@RequestParam(defaultValue = "") String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "pendingAmount") String sortBy,
			@RequestParam(defaultValue = "DESC") Sort.Direction direction) {

		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

		return invoiceService.getInvoicePendingList(security.orgId(), search.trim(), pageable);
	}

	@GetMapping("/client/{clientId}/page")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public Page<InvoiceListItem> getClientInvoicePage(
			@PathVariable String clientId,
			@RequestParam(defaultValue = "") String search,
			@RequestParam(required = false) InvoiceStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "invoiceDate") String sortBy,
			@RequestParam(defaultValue = "DESC") Sort.Direction direction
	) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

		return invoiceService.getClientInvoicePage(
				security.orgId(),
				clientId,
				status,
				search.trim(),
				pageable
		);
	}

	/* ===================== INVOICE PDF ================= */

	@GetMapping("/{invoiceNumber}/pdf")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public ResponseEntity<Resource> downloadInvoicePdf(@PathVariable String invoiceNumber) {

		PdfStream pdf = invoiceService.getInvoicePdf(invoiceNumber, security.orgId());

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + pdf.fileName())
				.contentType(MediaType.APPLICATION_PDF).contentLength(pdf.contentLength()).body(pdf.resource());

	}

	/* ===================== RELOAD INVOICE PDF ================= */

	@GetMapping("/reload/{bookingId}/pdf")
	@PreAuthorize("hasAuthority('INVOICE_REGENERATE')")
	public ResponseEntity<Resource> reloadInvoicePdf(@PathVariable String bookingId) {

		PdfStream pdf = invoiceService.reloadInvoicePdf(bookingId, security.orgId());

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + pdf.fileName())
				.contentType(MediaType.APPLICATION_PDF).contentLength(pdf.contentLength()).body(pdf.resource());

	}

	/* ===================== CREATE INVOICE ================= */

	@GetMapping("/generate/{bookingId}")
	@PreAuthorize("hasAuthority('INVOICE_GENERATE')")
	public void create(@PathVariable String bookingId) {
		invoiceService.createInvoice(bookingId, security.orgId());
	}
}

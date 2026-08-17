package com.core.controllers.client.app;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.core.dtos.client.app.ClientBookingDTO;
import com.core.dtos.client.app.ClientBookingDraftDTO;
import com.core.dtos.client.app.ClientBookingListDTO;
import com.core.dtos.common.PdfStream;
import com.core.mapper.ClientBookingAssembler;
import com.core.models.Client;
import com.core.models.User;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientBookingService;
import com.core.services.ClientService;
import com.core.services.InvoiceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/client/bookings")
@RequiredArgsConstructor
public class ClientBookingController {

	private final SecurityContextUtil security;
	private final ClientBookingService clientBookingService;
	private final ClientService clientService;
	private final ClientBookingAssembler clientBookingAssembler;
	private final InvoiceService invoiceService;

	/*
	 * ===================================================== GET – Client Booking
	 * History =====================================================
	 */

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public List<ClientBookingListDTO> getMyBookings() {

		Client client = clientService.findByUserId(security.userId());

		return clientBookingService.getMyBookings(client.getId(), client.getOrgId()).stream()
				.map(clientBookingAssembler::toBookingList).toList();
	}

	/*
	 * ========================= DETAIL (full booking) =========================
	 */

	@GetMapping("/{bookingId}")
	@PreAuthorize("isAuthenticated()")
	public ClientBookingDTO getBookingDetail(@PathVariable String bookingId) {

		var booking = clientBookingService.getBooking(bookingId, security.orgId());
		
		return clientBookingAssembler.toDTO(booking);
	}

	/*
	 * ===================================================== POST – Create Booking
	 * from Client App =====================================================
	 */

	@PostMapping
	@PreAuthorize("isAuthenticated()")
	public ClientBookingDTO draftBooking(@RequestBody ClientBookingDraftDTO form, Authentication authentication) {
		User user = (User) authentication.getPrincipal();
		Client client = clientService.findByUserId(user.getId());

		var booking = clientBookingService.draftBooking(form, client.getId(), client.getOrgId());

		return clientBookingAssembler.toDTO(booking);
	}
	
	@GetMapping("/invoice/{invoiceNumber}/pdf")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Resource> downloadInvoicePdf(@PathVariable String invoiceNumber) {
		PdfStream pdf = invoiceService.getInvoicePdf(invoiceNumber, security.orgId());
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + pdf.fileName())
				.contentType(MediaType.APPLICATION_PDF).contentLength(pdf.contentLength()).body(pdf.resource());
	}
}

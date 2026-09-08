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
import com.core.dtos.client.app.CancellationPreviewResponse;
import com.core.dtos.client.app.ClientCancelBookingRequest;
import com.core.dtos.client.app.TripRatingRequest;
import com.core.dtos.client.app.TripRatingResponse;
import com.core.dtos.client.app.TripShareLinkResponse;
import com.core.dtos.common.PdfStream;
import com.core.dtos.driverduty.DriverDutyLocationResponse;
import com.core.mapper.ClientBookingAssembler;
import com.core.models.Client;
import com.core.models.User;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientBookingService;
import com.core.services.ClientService;
import com.core.services.InvoiceService;
import com.core.services.TripShareService;

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
	private final TripShareService tripShareService;

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

		Client client = clientService.findByUserId(security.userId());
		var booking = clientBookingService.getOwnedBooking(bookingId, client.getId(), security.orgId());

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
		Client client = clientService.findByUserId(security.userId());
		PdfStream pdf = invoiceService.getInvoicePdfForClient(invoiceNumber, client.getId(), security.orgId());
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + pdf.fileName())
				.contentType(MediaType.APPLICATION_PDF).contentLength(pdf.contentLength()).body(pdf.resource());
	}

	/*
	 * REST fallback for the live-location WebSocket channel -- polled by the
	 * client after an extended disconnection, or on first render before any
	 * push has arrived.
	 */
	@GetMapping("/duty/{dutyId}/location")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DriverDutyLocationResponse> getDutyLocation(@PathVariable String dutyId) {
		Client client = clientService.findByUserId(security.userId());
		return clientBookingService.getDutyLocation(dutyId, client.getId(), security.orgId())
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/*
	 * ===================================================== RATING – post-trip
	 * driver rating (client-authored, one per duty) =====================================================
	 */

	@PostMapping("/duty/{dutyId}/rating")
	@PreAuthorize("isAuthenticated()")
	public TripRatingResponse submitRating(@PathVariable String dutyId, @RequestBody TripRatingRequest request) {
		Client client = clientService.findByUserId(security.userId());
		return clientBookingService.submitRating(dutyId, client.getId(), security.orgId(), request);
	}

	@GetMapping("/duty/{dutyId}/rating")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<TripRatingResponse> getRating(@PathVariable String dutyId) {
		Client client = clientService.findByUserId(security.userId());
		return clientBookingService.getRating(dutyId, client.getId(), security.orgId())
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/*
	 * ===================================================== TRIP SHARING
	 * =====================================================
	 */

	@PostMapping("/duty/{dutyId}/share")
	@PreAuthorize("isAuthenticated()")
	public TripShareLinkResponse createShareLink(@PathVariable String dutyId) {
		Client client = clientService.findByUserId(security.userId());
		return tripShareService.createShareLink(dutyId, client.getId(), security.orgId());
	}

	/*
	 * ===================================================== CANCELLATION
	 * =====================================================
	 */

	@GetMapping("/{bookingId}/cancel-preview")
	@PreAuthorize("isAuthenticated()")
	public CancellationPreviewResponse getCancelPreview(@PathVariable String bookingId) {
		Client client = clientService.findByUserId(security.userId());
		return clientBookingService.getCancellationPreview(bookingId, client.getId(), security.orgId());
	}

	@PostMapping("/{bookingId}/cancel")
	@PreAuthorize("isAuthenticated()")
	public void cancelBooking(@PathVariable String bookingId, @RequestBody ClientCancelBookingRequest request) {
		Client client = clientService.findByUserId(security.userId());
		clientBookingService.cancelBookingWithPolicy(bookingId, client.getId(), security.orgId(), request.reason());
	}
}

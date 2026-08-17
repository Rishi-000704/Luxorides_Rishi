package com.core.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;

import com.core.dtos.booking.*;
import com.core.security.SecurityContextUtil;
import com.core.services.BookingService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import com.core.dtos.common.PageResult;

@RestController
@RequestMapping("/booking/employee")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeBookingController {

	private final BookingService bookingService;
	private final SecurityContextUtil security;

	/* ===================== BOOKING ===================== */

	@PostMapping
	@PreAuthorize("hasAuthority('BOOKING_ADD')")
	public BookingDTO createBooking(@RequestBody BookingForm form) {
		return bookingService.addBooking(form, security.orgId());
	}

	@PostMapping("/update")
	@PreAuthorize("hasAuthority('BOOKING_EDIT')")
	public BookingDTO updateBooking(@RequestBody BookingForm form) {
		return bookingService.updateBooking(form, security.orgId());
	}

	/* ===================== DUTIES ===================== */

	@PostMapping("/add-duty")
	@PreAuthorize("hasAuthority('BOOKING_ADD_DUTY')")
	public BookingDTO addDuty(@RequestBody DutyForm form) {
		return bookingService.addBookingEntry(form, security.orgId());
	}

	@PostMapping("/update-duty")
	@PreAuthorize("hasAuthority('BOOKING_EDIT_DUTY')")
	public BookingDTO updateDuty(@RequestBody DutyForm form) {
		return bookingService.updateBookingEntry(form, security.orgId());
	}

	@PostMapping("/allot-duty")
	@PreAuthorize("hasAuthority('BOOKING_ALLOT_DUTY')")
	public BookingDTO allotDuty(@RequestBody AllotDutyCommand cmd) {
		return bookingService.allotDuty(cmd, security.orgId());
	}

	@PostMapping("/reallot-duty")
	@PreAuthorize("hasAuthority('BOOKING_REALLOT_DUTY')")
	public BookingDTO reAllotDuty(@RequestBody AllotDutyCommand cmd) {
		return bookingService.reAllotDuty(cmd, security.orgId());
	}

	@PostMapping(value = "/close-duty", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('BOOKING_CLOSE_DUTY')")
	public BookingDTO closeDuty(@ModelAttribute CloseDutyCommand cmd) {
		return bookingService.closeDuty(cmd, security.orgId());
	}

	@PostMapping(value = "/reclose-duty", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('BOOKING_RECLOSE_DUTY')")
	public BookingDTO reCloseDuty(@ModelAttribute CloseDutyCommand cmd) {
		return bookingService.reCloseDuty(cmd, security.orgId());
	}

	/* ===================== PAYMENTS ===================== */

	@PostMapping("/add-payment")
	@PreAuthorize("hasAuthority('BOOKING_ADD_PAYMENT')")
	public BookingDTO savePayment(@RequestBody BookingPaymentCommand cmd) {
		return bookingService.addPayment(cmd, security.orgId());
	}

	@PostMapping("/update-payment")
	@PreAuthorize("hasAuthority('BOOKING_EDIT_PAYMENT')")
	public BookingDTO updatePayment(@RequestBody BookingPaymentCommand cmd) {
		return bookingService.updatePayment(cmd, security.orgId());
	}

	@PostMapping("/confirm-payment")
	@PreAuthorize("hasAuthority('BOOKING_CONFIRM_PAYMENT')")
	public BookingDTO confirmPayment(@RequestParam String bookingId, @RequestParam String paymentId) {
		return bookingService.confirmPayment(bookingId, paymentId, security.orgId());
	}

	/* ===================== FETCH ===================== */

	@GetMapping("/{bookingId}")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public BookingDTO getProfile(@PathVariable String bookingId) {
		return bookingService.getBookingDTO(bookingId, security.orgId());
	}

	@PostMapping("/page")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public PageResult<BookingListItem> getPage(@RequestBody(required = false) BookingPageRequest request) {
		return bookingService.getPage(security.orgId(), request == null ? BookingPageRequest.defaults() : request);
	}

	@PostMapping("/duties/page")
	@PreAuthorize("hasAuthority('BOOKING_VIEW')")
	public PageResult<DutyListItem> getDutyPage(@RequestBody(required = false) DutyPageRequest request) {
		return bookingService.getDutyPage(security.orgId(), request == null ? DutyPageRequest.defaults() : request);
	}

	/* ===================== BOOKING STATUS ===================== */

	@PostMapping("/confirm-booking")
	@PreAuthorize("hasAuthority('BOOKING_CONFIRM')")
	public ResponseEntity<Void> confirmBooking(@RequestParam String bookingId) {
		bookingService.confirmBooking(bookingId, security.orgId());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/cancel-booking")
	@PreAuthorize("hasAuthority('BOOKING_CANCEL')")
	public ResponseEntity<Void> cancelBooking(@RequestParam String bookingId, @RequestParam String reason) {
		bookingService.cancelBooking(bookingId, security.orgId(), reason);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/complete-booking")
	@PreAuthorize("hasAuthority('BOOKING_COMPLETE')")
	public ResponseEntity<Void> completeBooking(@RequestParam String bookingId) {
		bookingService.completeBooking(bookingId, security.orgId());
		return ResponseEntity.ok().build();
	}

}

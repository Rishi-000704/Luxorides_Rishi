package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.models.BookingEntry;
import com.core.models.Booking;
import com.core.models.Client;
import com.core.models.Package;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.DutyType;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.ClientBillingEntityRepository;
import com.core.repositories.ClientRepository;
import com.core.repositories.OrgBillingEntityRepository;
import com.core.repositories.PackageRepository;
import com.core.repositories.PaymentOutRepository;
import com.core.repositories.PurchaseInvoiceEntryRepository;
import com.core.repositories.PurchaseInvoiceRepository;
import com.core.services.PurchaseInvoiceService.PurchasePackageOptionsForDuty;
import com.core.services.common.PdfService;

/*
 * Phase A -- getPackageOptionsForDuties is the bulk counterpart to the
 * existing single-duty getPackageOptions, added to collapse the
 * purchase-invoice draft flow's N separate GET .../packages requests (one
 * per selected duty) into one request. These prove: (1) the vendor is
 * validated exactly once for the whole batch, not once per duty like N
 * separate single-duty calls would do; (2) results come back one per
 * requested id, in request order; (3) one duty failing its own validation
 * (already invoiced, wrong vendor, etc.) is reported on that entry alone,
 * not thrown as a whole-batch exception -- matching how the existing
 * per-duty frontend calls already fail independently of each other.
 */
class PurchaseInvoiceServiceBulkPackageOptionsTest {

	private static final String ORG_ID = "org-1";
	private static final String VENDOR_ID = "vendor-1";

	private PurchaseInvoiceEntryRepository purchaseInvoiceEntryRepository;
	private BookingEntryRepository bookingEntryRepository;
	private PackageRepository packageRepository;
	private ClientRepository clientRepository;
	private PurchaseInvoiceService service;

	@BeforeEach
	void setUp() {
		PurchaseInvoiceRepository purchaseInvoiceRepository = mock(PurchaseInvoiceRepository.class);
		purchaseInvoiceEntryRepository = mock(PurchaseInvoiceEntryRepository.class);
		PaymentOutRepository paymentOutRepository = mock(PaymentOutRepository.class);
		bookingEntryRepository = mock(BookingEntryRepository.class);
		packageRepository = mock(PackageRepository.class);
		clientRepository = mock(ClientRepository.class);
		ClientBillingEntityRepository clientBillingEntityRepository = mock(ClientBillingEntityRepository.class);
		OrgBillingEntityRepository orgBillingEntityRepository = mock(OrgBillingEntityRepository.class);
		PdfService pdfService = mock(PdfService.class);

		service = new PurchaseInvoiceService(
				purchaseInvoiceRepository, purchaseInvoiceEntryRepository, paymentOutRepository,
				bookingEntryRepository, packageRepository, clientRepository, clientBillingEntityRepository,
				orgBillingEntityRepository, pdfService);

		Client vendor = new Client();
		vendor.setId(VENDOR_ID);
		vendor.setSupplier(true);
		when(clientRepository.findByIdAndOrgId(VENDOR_ID, ORG_ID)).thenReturn(Optional.of(vendor));
	}

	private BookingEntry completedDuty(String bookingEntryId, String masterVehicleId) {
		BookingEntry entry = new BookingEntry();
		entry.setId(bookingEntryId);
		entry.setSupplierId(VENDOR_ID);
		entry.setStatus(DutyStatus.COMPLETED);
		entry.setMasterVehicleId(masterVehicleId);
		entry.setBooking(new Booking());

		PackageSnapshot snapshot = new PackageSnapshot();
		snapshot.setDutyType(DutyType.LOCAL);
		entry.setPack(snapshot);

		return entry;
	}

	@Test
	void validatesVendorExactlyOnce_forTheWholeBatch() {
		when(purchaseInvoiceEntryRepository.existsByActiveBookingEntryId(any())).thenReturn(false);
		when(bookingEntryRepository.lockByIdAndOrgId(eq("duty-1"), eq(ORG_ID)))
				.thenReturn(Optional.of(completedDuty("duty-1", "mv-1")));
		when(bookingEntryRepository.lockByIdAndOrgId(eq("duty-2"), eq(ORG_ID)))
				.thenReturn(Optional.of(completedDuty("duty-2", "mv-2")));
		when(packageRepository.findPurchasePackageOptions(any(), any(), any(), any())).thenReturn(List.of());

		service.getPackageOptionsForDuties(VENDOR_ID, List.of("duty-1", "duty-2"), ORG_ID);

		verify(clientRepository, times(1)).findByIdAndOrgId(VENDOR_ID, ORG_ID);
	}

	@Test
	void returnsOneResultPerRequestedDuty_inRequestOrder_withCorrectPackages() {
		Package pkg1 = new Package();
		pkg1.setId("pkg-1");
		Package pkg2 = new Package();
		pkg2.setId("pkg-2");

		when(purchaseInvoiceEntryRepository.existsByActiveBookingEntryId(any())).thenReturn(false);
		when(bookingEntryRepository.lockByIdAndOrgId(eq("duty-1"), eq(ORG_ID)))
				.thenReturn(Optional.of(completedDuty("duty-1", "mv-1")));
		when(bookingEntryRepository.lockByIdAndOrgId(eq("duty-2"), eq(ORG_ID)))
				.thenReturn(Optional.of(completedDuty("duty-2", "mv-2")));
		when(packageRepository.findPurchasePackageOptions(ORG_ID, VENDOR_ID, "mv-1", DutyType.LOCAL))
				.thenReturn(List.of(pkg1));
		when(packageRepository.findPurchasePackageOptions(ORG_ID, VENDOR_ID, "mv-2", DutyType.LOCAL))
				.thenReturn(List.of(pkg2));

		List<PurchasePackageOptionsForDuty> results =
				service.getPackageOptionsForDuties(VENDOR_ID, List.of("duty-1", "duty-2"), ORG_ID);

		assertEquals(2, results.size());
		assertEquals("duty-1", results.get(0).bookingEntryId());
		assertEquals(List.of(pkg1), results.get(0).packages());
		assertNull(results.get(0).error());
		assertEquals("duty-2", results.get(1).bookingEntryId());
		assertEquals(List.of(pkg2), results.get(1).packages());
		assertNull(results.get(1).error());
	}

	@Test
	void onePreviouslyInvoicedDuty_failsOnlyThatEntry_restOfBatchStillSucceeds() {
		when(purchaseInvoiceEntryRepository.existsByActiveBookingEntryId("duty-already-invoiced")).thenReturn(true);
		when(purchaseInvoiceEntryRepository.existsByActiveBookingEntryId("duty-ok")).thenReturn(false);
		when(bookingEntryRepository.lockByIdAndOrgId(eq("duty-ok"), eq(ORG_ID)))
				.thenReturn(Optional.of(completedDuty("duty-ok", "mv-1")));
		when(packageRepository.findPurchasePackageOptions(any(), any(), any(), any())).thenReturn(List.of());

		List<PurchasePackageOptionsForDuty> results = service.getPackageOptionsForDuties(
				VENDOR_ID, List.of("duty-already-invoiced", "duty-ok"), ORG_ID);

		assertEquals(2, results.size());

		PurchasePackageOptionsForDuty failed = results.get(0);
		assertEquals("duty-already-invoiced", failed.bookingEntryId());
		assertTrue(failed.packages().isEmpty());
		assertNotNull(failed.error());

		PurchasePackageOptionsForDuty ok = results.get(1);
		assertEquals("duty-ok", ok.bookingEntryId());
		assertNull(ok.error());

		// The failure on the first duty never touched the locking/lookup path
		// for it -- confirms existsByActiveBookingEntryId short-circuits
		// before lockByIdAndOrgId, same as the single-duty method.
		verify(bookingEntryRepository, times(0)).lockByIdAndOrgId(eq("duty-already-invoiced"), any());
	}
}

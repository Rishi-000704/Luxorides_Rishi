package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamResource;

import com.core.dtos.common.PdfStream;
import com.core.exception.BusinessException;
import com.core.location.orchestrator.GeoProviderChain;
import com.core.models.Invoice;
import com.core.repositories.BookingRepository;
import com.core.repositories.FinancialYearRepository;
import com.core.repositories.InvoiceRepository;
import com.core.repositories.OrgBillingEntityRepository;
import com.core.services.common.PdfService;

/*
 * P0 IDOR fix -- getInvoicePdf(invoiceNumber, orgId) is org-scoped only
 * (correct for EmployeeInvoiceController, an org-wide employee view). The
 * customer-facing download must additionally prove the requesting client
 * owns the invoice, since invoice numbers are a sequential, easily guessed
 * counter -- this covers getInvoicePdfForClient, the method
 * ClientBookingController.downloadInvoicePdf now calls instead.
 */
class InvoiceServiceOwnershipTest {

	private static final String ORG_ID = "org-1";
	private static final String INVOICE_NUMBER = "INV-000042";
	private static final String OWNING_CLIENT_ID = "client-owner";
	private static final String OTHER_CLIENT_ID = "client-other";

	private InvoiceRepository invoiceRepository;
	private PdfService pdfService;
	private InvoiceService service;

	@BeforeEach
	void setUp() {
		invoiceRepository = mock(InvoiceRepository.class);
		BookingRepository bookingRepository = mock(BookingRepository.class);
		FinancialYearRepository financialYearRepository = mock(FinancialYearRepository.class);
		OrgBillingEntityRepository orgBillingEntityRepository = mock(OrgBillingEntityRepository.class);
		ClientService clientService = mock(ClientService.class);
		pdfService = mock(PdfService.class);
		GeoProviderChain geoProviderChain = mock(GeoProviderChain.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

		service = new InvoiceService(invoiceRepository, bookingRepository, financialYearRepository,
				orgBillingEntityRepository, clientService, pdfService, geoProviderChain, eventPublisher);
	}

	private Invoice invoiceOwnedBy(String clientId) {
		Invoice invoice = new Invoice();
		invoice.setOrgId(ORG_ID);
		invoice.setInvoiceNumber(INVOICE_NUMBER);
		invoice.setClientId(clientId);
		return invoice;
	}

	@Test
	void getInvoicePdfForClient_ownerRequestsTheirOwnInvoice_returnsIt() {
		when(invoiceRepository.findInvoiceByInvoiceNumberAndOrgId(INVOICE_NUMBER, ORG_ID))
				.thenReturn(Optional.of(invoiceOwnedBy(OWNING_CLIENT_ID)));

		PdfStream expected = new PdfStream(mock(InputStreamResource.class), 100L, "invoice.pdf");
		when(pdfService.generateInvoicePdfStream(org.mockito.ArgumentMatchers.any(Invoice.class)))
				.thenReturn(expected);

		PdfStream result = service.getInvoicePdfForClient(INVOICE_NUMBER, OWNING_CLIENT_ID, ORG_ID);

		assertEquals(expected, result);
	}

	@Test
	void getInvoicePdfForClient_anotherClientInSameOrgRequestsIt_isDenied_notTheOtherClientsInvoice() {
		when(invoiceRepository.findInvoiceByInvoiceNumberAndOrgId(INVOICE_NUMBER, ORG_ID))
				.thenReturn(Optional.of(invoiceOwnedBy(OWNING_CLIENT_ID)));

		assertThrows(BusinessException.class,
				() -> service.getInvoicePdfForClient(INVOICE_NUMBER, OTHER_CLIENT_ID, ORG_ID));
	}

	@Test
	void getInvoicePdfForClient_invoiceDoesNotExistInOrg_isRejected() {
		when(invoiceRepository.findInvoiceByInvoiceNumberAndOrgId(INVOICE_NUMBER, "different-org"))
				.thenReturn(Optional.empty());

		assertThrows(RuntimeException.class,
				() -> service.getInvoicePdfForClient(INVOICE_NUMBER, OWNING_CLIENT_ID, "different-org"));
	}
}

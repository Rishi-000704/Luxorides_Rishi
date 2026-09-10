package com.core.controllers;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.purchase.AddPaymentOutCommand;
import com.core.dtos.purchase.CompletePurchaseInvoiceCommand;
import com.core.dtos.purchase.CreatePurchaseInvoiceDraftCommand;
import com.core.dtos.purchase.EligiblePurchaseDutyDTO;
import com.core.dtos.purchase.PurchaseInvoiceDTO;
import com.core.dtos.purchase.PurchasePackageOptionDTO;
import com.core.dtos.purchase.PurchasePackageOptionsBulkRequest;
import com.core.dtos.purchase.PurchasePackageOptionsForDutyDTO;
import com.core.mapper.PurchaseInvoiceAssembler;
import com.core.models.enums.PurchaseInvoiceStatus;
import com.core.security.SecurityContextUtil;
import com.core.services.PurchaseInvoiceService;
import org.springframework.core.io.Resource;
import com.core.dtos.common.PdfStream;

import lombok.RequiredArgsConstructor;
import com.core.models.PurchaseInvoice;
import com.core.models.enums.Authority;

@RestController
@RequestMapping("/purchase-invoice")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseInvoiceService;
    private final PurchaseInvoiceAssembler assembler;
    private final SecurityContextUtil security;

    /* ===================== DUTY SELECTION ===================== */

    @GetMapping("/vendors/{vendorId}/eligible-duties")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_VIEW')")
    public List<EligiblePurchaseDutyDTO> eligibleDuties(@PathVariable String vendorId) {
        return purchaseInvoiceService.getEligibleDuties(vendorId, security.orgId())
                .stream()
                .map(assembler::eligibleDuty)
                .toList();
    }

    @GetMapping("/vendors/{vendorId}/duties/{bookingEntryId}/packages")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_VIEW')")
    public List<PurchasePackageOptionDTO> packageOptions(
            @PathVariable String vendorId,
            @PathVariable String bookingEntryId
    ) {
        return purchaseInvoiceService.getPackageOptions(vendorId, bookingEntryId, security.orgId())
                .stream()
                .map(assembler::packageOption)
                .toList();
    }

    /*
     * Phase A: bulk counterpart to the endpoint above. The purchase-invoice
     * draft flow's step 3 selects N duties for one vendor and previously
     * fired N separate GET .../duties/{bookingEntryId}/packages requests to
     * load their package options -- this collapses that into one request.
     */
    @PostMapping("/vendors/{vendorId}/duties/packages")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_VIEW')")
    public List<PurchasePackageOptionsForDutyDTO> packageOptionsBulk(
            @PathVariable String vendorId,
            @RequestBody PurchasePackageOptionsBulkRequest request
    ) {
        List<String> bookingEntryIds = request.bookingEntryIds() == null ? List.of() : request.bookingEntryIds();
        return purchaseInvoiceService.getPackageOptionsForDuties(vendorId, bookingEntryIds, security.orgId())
                .stream()
                .map(assembler::packageOptionsForDuty)
                .toList();
    }

    /* ===================== PURCHASE INVOICE ===================== */

    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_ADD')")
    public PurchaseInvoiceDTO createDraft(@RequestBody CreatePurchaseInvoiceDraftCommand cmd) {
        return assembleForCurrentUser(
                purchaseInvoiceService.createDraft(
                        cmd,
                        security.orgId()
                )
        );
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_COMPLETE')")
    public PurchaseInvoiceDTO complete(
            @PathVariable String id,
            @RequestBody(required = false) CompletePurchaseInvoiceCommand cmd
    ) {
        return assembleForCurrentUser(
                purchaseInvoiceService.complete(
                        id,
                        cmd,
                        security.orgId()
                )
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_CANCEL')")
    public PurchaseInvoiceDTO cancel(
            @PathVariable String id,
            @RequestParam(defaultValue = "") String reason
    ) {
        return assembleForCurrentUser(
                purchaseInvoiceService.cancel(
                        id,
                        reason,
                        security.orgId()
                )
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_VIEW')")
    public PurchaseInvoiceDTO get(@PathVariable String id) {
        return assembleForCurrentUser(
                purchaseInvoiceService.get(
                        id,
                        security.orgId()
                )
        );
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_VIEW')")
    public ResponseEntity<Resource> downloadPurchaseInvoicePdf(@PathVariable String id) {
        PdfStream pdf = purchaseInvoiceService.getPurchaseInvoicePdf(id, security.orgId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdf.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.contentLength())
                .body(pdf.resource());
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_VIEW')")
    public Page<PurchaseInvoiceDTO> page(
            @RequestParam(required = false) String vendorId,
            @RequestParam(required = false) PurchaseInvoiceStatus status,
            @RequestParam(defaultValue = "") String searchstr,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return purchaseInvoiceService
                .getPage(
                        security.orgId(),
                        vendorId,
                        status,
                        searchstr.trim(),
                        pageable
                )
                .map(this::assembleForCurrentUser);
    }

    /* ===================== PAYMENT OUT ===================== */

    @PostMapping("/payment-out")
    @PreAuthorize("hasAuthority('PAYMENT_OUT_ADD')")
    public PurchaseInvoiceDTO addPaymentOut(
            @RequestBody AddPaymentOutCommand cmd
    ) {
        purchaseInvoiceService.addPaymentOut(
                cmd,
                security.orgId()
        );

        return assembleForCurrentUser(
                purchaseInvoiceService.get(
                        cmd.purchaseInvoiceId(),
                        security.orgId()
                )
        );
    }

    private PurchaseInvoiceDTO assembleForCurrentUser(
            PurchaseInvoice invoice
    ) {
        return assembler.assemble(
                invoice,
                security.hasAuthority(Authority.PAYMENT_OUT_VIEW)
        );
    }
}

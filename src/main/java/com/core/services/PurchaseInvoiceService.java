package com.core.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.purchase.AddPaymentOutCommand;
import com.core.dtos.purchase.CompletePurchaseInvoiceCommand;
import com.core.dtos.purchase.CreatePurchaseInvoiceDraftCommand;
import com.core.dtos.purchase.PurchaseInvoiceEntryCommand;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.ExtraCharge;
import com.core.models.PaymentOut;
import com.core.models.PurchaseInvoice;
import com.core.models.PurchaseInvoiceEntry;
import com.core.models.PurchaseInvoiceExtraCharge;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.DutyType;
import com.core.models.enums.GstType;
import com.core.models.enums.PackageScope;
import com.core.models.enums.PaymentGateway;
import com.core.models.enums.PaymentStatus;
import com.core.models.enums.PurchaseInvoiceStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.ClientBillingEntityRepository;
import com.core.repositories.ClientRepository;
import com.core.repositories.OrgBillingEntityRepository;
import com.core.repositories.PackageRepository;
import com.core.repositories.PaymentOutRepository;
import com.core.repositories.PurchaseInvoiceEntryRepository;
import com.core.repositories.PurchaseInvoiceRepository;
import com.core.util.PackageUtil;
import com.core.dtos.common.PdfStream;
import com.core.services.common.PdfService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PurchaseInvoiceEntryRepository purchaseInvoiceEntryRepository;
    private final PaymentOutRepository paymentOutRepository;
    private final BookingEntryRepository bookingEntryRepository;
    private final PackageRepository packageRepository;
    private final ClientRepository clientRepository;
    private final ClientBillingEntityRepository clientBillingEntityRepository;
    private final OrgBillingEntityRepository orgBillingEntityRepository;
    private final PdfService pdfService;

    @Transactional(readOnly = true)
    public List<BookingEntry> getEligibleDuties(String vendorId, String orgId) {
        validateVendor(orgId, vendorId);

        return bookingEntryRepository.findVendorCompletedDutiesForPurchase(orgId, vendorId, DutyStatus.COMPLETED)
                .stream()
                .filter(entry -> !purchaseInvoiceEntryRepository.existsByActiveBookingEntryId(entry.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.core.models.Package> getPackageOptions(String vendorId, String bookingEntryId, String orgId) {
        validateVendor(orgId, vendorId);
        return getPackageOptionsForDuty(vendorId, bookingEntryId, orgId);
    }

    /*
     * Phase A: bulk counterpart used by the purchase-invoice draft flow's
     * step 3 (consolidating N selected duties into one vendor invoice), which
     * previously fired one GET .../duties/{bookingEntryId}/packages per
     * selected duty. Validates the vendor exactly once (not once per duty,
     * unlike N separate calls to the single-duty method above would), then
     * reuses the exact same per-duty validation/lookup as the single-duty
     * endpoint for each requested duty. A duty that fails its own validation
     * (already invoiced, wrong vendor, missing package snapshot, etc.) is
     * reported per-entry rather than aborting the whole batch, matching how
     * the existing per-duty frontend calls already fail independently of
     * each other.
     */
    @Transactional(readOnly = true)
    public List<PurchasePackageOptionsForDuty> getPackageOptionsForDuties(
            String vendorId, List<String> bookingEntryIds, String orgId) {
        validateVendor(orgId, vendorId);

        List<PurchasePackageOptionsForDuty> results = new ArrayList<>();
        for (String bookingEntryId : bookingEntryIds) {
            try {
                results.add(new PurchasePackageOptionsForDuty(
                        bookingEntryId,
                        getPackageOptionsForDuty(vendorId, bookingEntryId, orgId),
                        null
                ));
            } catch (BusinessException | NotFoundException e) {
                results.add(new PurchasePackageOptionsForDuty(bookingEntryId, List.of(), e.getMessage()));
            }
        }
        return results;
    }

    public record PurchasePackageOptionsForDuty(
            String bookingEntryId,
            List<com.core.models.Package> packages,
            String error
    ) {
    }

    private List<com.core.models.Package> getPackageOptionsForDuty(String vendorId, String bookingEntryId, String orgId) {
        if (purchaseInvoiceEntryRepository.existsByActiveBookingEntryId(bookingEntryId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty is already included in a purchase invoice");
        }

        BookingEntry duty = bookingEntryRepository.lockByIdAndOrgId(bookingEntryId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found"));

        validateDutyForPurchase(duty, vendorId);

        DutyType dutyType = duty.getPack() == null ? null : duty.getPack().getDutyType();
        if (dutyType == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty package snapshot is missing");
        }

        return packageRepository.findPurchasePackageOptions(
                orgId,
                vendorId,
                duty.getMasterVehicleId(),
                dutyType
        );
    }

    @Transactional
    public PurchaseInvoice createDraft(CreatePurchaseInvoiceDraftCommand cmd, String orgId) {
        validateCreateCommand(cmd);
        validateVendor(orgId, cmd.vendorId());
        validateVendorBillingEntity(orgId, cmd.vendorId(), cmd.vendorBillingEntityId());
        validateOrgBillingEntity(orgId, cmd.orgBillingEntityId());
        validateVendorInvoiceNumberUnique(orgId, cmd.vendorId(), cmd.vendorInvoiceNumber());

        List<String> bookingEntryIds = cmd.entries()
                .stream()
                .map(PurchaseInvoiceEntryCommand::bookingEntryId)
                .toList();

        validateNoDuplicateDutiesInRequest(bookingEntryIds);
        validateDutiesNotAlreadyInvoiced(bookingEntryIds);

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setOrgId(orgId);
        invoice.setVendorId(cmd.vendorId());
        invoice.setVendorBillingEntityId(cmd.vendorBillingEntityId());
        invoice.setOrgBillingEntityId(cmd.orgBillingEntityId());
        invoice.setPurchaseInvoiceNumber(generatePurchaseInvoiceNumber());
        invoice.setVendorInvoiceNumber(blankToNull(cmd.vendorInvoiceNumber()));
        invoice.setVendorInvoiceDate(cmd.vendorInvoiceDate());
        invoice.setInvoiceDate(cmd.invoiceDate() == null ? Instant.now() : cmd.invoiceDate());
        invoice.setDueDate(cmd.dueDate());
        invoice.setRemarks(cmd.remarks());
        invoice.setStatus(PurchaseInvoiceStatus.DRAFT);
        invoice.setEntries(new ArrayList<>());
        invoice.setPayments(new ArrayList<>());

        for (PurchaseInvoiceEntryCommand entryCmd : cmd.entries()) {
            BookingEntry duty = bookingEntryRepository.lockByIdAndOrgId(entryCmd.bookingEntryId(), orgId)
                    .orElseThrow(() -> new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found: " + entryCmd.bookingEntryId()));

            validateDutyForPurchase(duty, cmd.vendorId());

            if (purchaseInvoiceEntryRepository.existsByActiveBookingEntryId(duty.getId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty is already included in a purchase invoice: " + duty.getDutyId());
            }

            com.core.models.Package purchasePackage = resolvePurchasePackage(
                    orgId,
                    cmd.vendorId(),
                    duty,
                    entryCmd.packageId()
            );

            PurchaseInvoiceEntry entry = buildEntry(invoice, duty, purchasePackage, entryCmd.remarks());
            invoice.getEntries().add(entry);
        }

        recalculateInvoice(invoice, cmd.gstType(), cmd.gstRate());

        return purchaseInvoiceRepository.save(invoice);
    }

    @Transactional
    public PurchaseInvoice complete(String purchaseInvoiceId, CompletePurchaseInvoiceCommand cmd, String orgId) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.lockByIdAndOrgId(purchaseInvoiceId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Purchase invoice not found"));

        if (invoice.getStatus() != PurchaseInvoiceStatus.DRAFT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only draft purchase invoice can be completed");
        }

        if (cmd != null) {
            if (cmd.vendorInvoiceNumber() != null && !cmd.vendorInvoiceNumber().isBlank()) {
                validateVendorInvoiceNumberUnique(orgId, invoice.getVendorId(), cmd.vendorInvoiceNumber());
                invoice.setVendorInvoiceNumber(cmd.vendorInvoiceNumber().trim());
            }

            if (cmd.vendorInvoiceDate() != null) {
                invoice.setVendorInvoiceDate(cmd.vendorInvoiceDate());
            }

            if (cmd.dueDate() != null) {
                invoice.setDueDate(cmd.dueDate());
            }

            if (cmd.remarks() != null) {
                invoice.setRemarks(cmd.remarks());
            }
        }

        if (invoice.getVendorInvoiceNumber() == null || invoice.getVendorInvoiceNumber().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Vendor invoice number is required before completion");
        }

        if (invoice.getEntries() == null || invoice.getEntries().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Purchase invoice cannot be completed without duties");
        }

        invoice.setStatus(PurchaseInvoiceStatus.COMPLETED);

        return purchaseInvoiceRepository.save(invoice);
    }

    @Transactional
    public PurchaseInvoice cancel(String purchaseInvoiceId, String reason, String orgId) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.lockByIdAndOrgId(purchaseInvoiceId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Purchase invoice not found"));

        if (invoice.getStatus() == PurchaseInvoiceStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Paid purchase invoice cannot be cancelled directly");
        }

        invoice.setStatus(PurchaseInvoiceStatus.CANCELLED);

        if (reason != null && !reason.isBlank()) {
            invoice.setRemarks(reason.trim());
        }

        if (invoice.getEntries() != null) {
            for (PurchaseInvoiceEntry entry : invoice.getEntries()) {
                entry.setActiveBookingEntryId(null);
            }
        }

        return purchaseInvoiceRepository.save(invoice);
    }

    @Transactional
    public PaymentOut addPaymentOut(AddPaymentOutCommand cmd, String orgId) {
        validatePaymentOutCommand(cmd);

        PurchaseInvoice invoice = purchaseInvoiceRepository.lockByIdAndOrgId(cmd.purchaseInvoiceId(), orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Purchase invoice not found"));

        if (invoice.getStatus() != PurchaseInvoiceStatus.COMPLETED
                && invoice.getStatus() != PurchaseInvoiceStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Payment out can be added only against completed purchase invoice");
        }

        BigDecimal paid = amount(cmd.paidAmount());
        BigDecimal tds = amount(cmd.tds());

        BigDecimal settlement = paid.add(tds).setScale(0, RoundingMode.HALF_UP);
        BigDecimal currentPaid = amount(invoice.getPaidAmount());
        BigDecimal grandTotal = amount(invoice.getGrandTotal());
        BigDecimal newPaid = currentPaid.add(settlement).setScale(0, RoundingMode.HALF_UP);

        if (newPaid.compareTo(grandTotal) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Payment out exceeds purchase invoice balance");
        }

        PaymentOut payment = new PaymentOut();
        payment.setOrgId(orgId);
        payment.setVendorId(invoice.getVendorId());
        payment.setPurchaseInvoice(invoice);
        payment.setPaymentMode(cmd.paymentMode());
        payment.setTransactionNumber(blankToNull(cmd.transactionNumber()));
        payment.setTransactionDate(cmd.transactionDate() == null ? Instant.now() : cmd.transactionDate());
        payment.setPaidAmount(cmd.paidAmount());
        payment.setTds(cmd.tds() == null ? Money.INR(BigDecimal.ZERO) : cmd.tds());
        payment.setRemarks(cmd.remarks());
        payment.setGateway(PaymentGateway.MANUAL_ENTRY);
        payment.setStatus(PaymentStatus.CONFIRMED);

        if (invoice.getPayments() == null) {
            invoice.setPayments(new ArrayList<>());
        }
        invoice.getPayments().add(payment);

        invoice.setPaidAmount(Money.INR(newPaid));
        invoice.setBalanceAmount(Money.INR(grandTotal.subtract(newPaid)));
        invoice.setStatus(
                newPaid.compareTo(grandTotal) >= 0
                        ? PurchaseInvoiceStatus.PAID
                        : PurchaseInvoiceStatus.COMPLETED
        );

        purchaseInvoiceRepository.save(invoice);

        return paymentOutRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PurchaseInvoice get(String id, String orgId) {
        return purchaseInvoiceRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Purchase invoice not found"));
    }

    @Transactional(readOnly = true)
    public PdfStream getPurchaseInvoicePdf(String purchaseInvoiceId, String orgId) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findDetailedByIdAndOrgId(purchaseInvoiceId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Purchase invoice not found"));

        return pdfService.generatePurchaseInvoicePdfStream(invoice);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseInvoice> getPage(
            String orgId,
            String vendorId,
            PurchaseInvoiceStatus status,
            String search,
            Pageable pageable
    ) {
        String normalizedSearch = search == null || search.isBlank() ? "" : search.trim();
        String normalizedVendorId = vendorId == null || vendorId.isBlank() ? null : vendorId.trim();
        return purchaseInvoiceRepository.getPage(orgId, normalizedVendorId, status, normalizedSearch, pageable);
    }

    private PurchaseInvoiceEntry buildEntry(
            PurchaseInvoice invoice,
            BookingEntry duty,
            com.core.models.Package purchasePackage,
            String remarks
    ) {
        PackageSnapshot purchasePack = PackageUtil.toPackageSnapshot(purchasePackage);

        PurchaseInvoiceEntry entry = new PurchaseInvoiceEntry();
        entry.setPurchaseInvoice(invoice);
        entry.setOrgId(invoice.getOrgId());
        entry.setVendorId(invoice.getVendorId());

        entry.setBookingId(duty.getBooking().getBookingId());
        entry.setBookingEntryId(duty.getId());
        entry.setActiveBookingEntryId(duty.getId());
        entry.setDutyId(duty.getDutyId());

        entry.setPack(purchasePack);
        entry.setMasterVehicleId(duty.getMasterVehicleId());

        entry.setReportingTime(duty.getReportingTime());
        entry.setReportingLocation(duty.getReportingLocation());
        entry.setDropTime(duty.getDropTime());
        entry.setDropLocation(duty.getDropLocation());

        entry.setStartingKM(duty.getStartingKM());
        entry.setClosingKM(duty.getClosingKM());
        entry.setStartAt(duty.getStartAt());
        entry.setEndAt(duty.getEndAt());

        entry.setFlightNumber(duty.getFlightNumber());
        entry.setSupplierId(duty.getSupplierId());
        entry.setFleetVehicleId(duty.getFleetVehicleId());
        entry.setDriverId(duty.getDriverId());

        entry.setRunningDays(duty.getRunningDays());
        entry.setExtraChargebleDistance(duty.getExtraChargebleDistance());
        entry.setExtraChargebleTime(duty.getExtraChargebleTime());
        entry.setNightChargeble(duty.getNightChargeble());

        entry.setPassengerIds(
                duty.getPassengerIds() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(duty.getPassengerIds())
        );

        entry.setClientNotes(duty.getClientNotes());
        entry.setRemarks(remarks);

        calculateEntryAmounts(entry, duty, purchasePack);
        syncExtraCharges(duty, entry);

        return entry;
    }

    private void calculateEntryAmounts(PurchaseInvoiceEntry entry, BookingEntry duty, PackageSnapshot pack) {
        if (pack == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Purchase package snapshot is required");
        }

        BigDecimal runningDays = BigDecimal.valueOf(duty.getRunningDays() == null ? 1 : duty.getRunningDays());

        BigDecimal base = amount(pack.getBaseFare()).multiply(runningDays);

        BigDecimal extraDistance = BigDecimal
                .valueOf(duty.getExtraChargebleDistance() == null ? 0 : duty.getExtraChargebleDistance())
                .multiply(amount(pack.getExtraPerKM()));

        BigDecimal extraTime = BigDecimal
                .valueOf(duty.getExtraChargebleTime() == null ? 0F : duty.getExtraChargebleTime())
                .multiply(amount(pack.getExtraPerHS()));

        BigDecimal night = Boolean.TRUE.equals(duty.getNightChargeble())
                ? amount(pack.getNightCharge())
                : BigDecimal.ZERO;

        BigDecimal extras = sumExtraCharges(duty);

        BigDecimal total = base
                .add(extraDistance)
                .add(extraTime)
                .add(night)
                .add(extras)
                .setScale(0, RoundingMode.HALF_UP);

        entry.setChargebleBaseFare(Money.INR(base));
        entry.setExtraChargeDistance(Money.INR(extraDistance));
        entry.setExtraChargeTime(Money.INR(extraTime));
        entry.setNightCharge(Money.INR(night));
        entry.setExtraChargesTotal(Money.INR(extras));
        entry.setDutyTotal(Money.INR(total));
    }

    private void syncExtraCharges(BookingEntry duty, PurchaseInvoiceEntry entry) {
        entry.setCharges(new ArrayList<>());

        if (duty.getCharges() == null || duty.getCharges().isEmpty()) {
            return;
        }

        for (ExtraCharge charge : duty.getCharges()) {
            PurchaseInvoiceExtraCharge copy = new PurchaseInvoiceExtraCharge();
            copy.setPurchaseInvoiceEntry(entry);
            copy.setDescription(charge.getDescription());
            copy.setAmount(charge.getAmount());
            entry.getCharges().add(copy);
        }
    }

    private void recalculateInvoice(PurchaseInvoice invoice, GstType gstType, Integer gstRate) {
        BigDecimal taxable = BigDecimal.ZERO;

        for (PurchaseInvoiceEntry entry : invoice.getEntries()) {
            taxable = taxable.add(amount(entry.getDutyTotal()));
        }

        taxable = taxable.setScale(0, RoundingMode.HALF_UP);

        GstType safeGstType = gstType == null ? GstType.EXEMPT : gstType;
        Integer safeGstRate = gstRate == null ? 0 : gstRate;

        GstSnapshot gstSnapshot = GstSnapshot.of(safeGstType, taxable, safeGstRate);
        BigDecimal grandTotal = taxable
                .add(gstSnapshot.getTotalTax())
                .setScale(0, RoundingMode.HALF_UP);

        invoice.setSubtotal(Money.INR(taxable));
        invoice.setTaxableAmount(Money.INR(taxable));
        invoice.setGstSnapshot(gstSnapshot);
        invoice.setGrandTotal(Money.INR(grandTotal));
        invoice.setPaidAmount(Money.INR(BigDecimal.ZERO));
        invoice.setBalanceAmount(Money.INR(grandTotal));
    }

    private void validateCreateCommand(CreatePurchaseInvoiceDraftCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Purchase invoice command is required");
        }

        if (cmd.vendorId() == null || cmd.vendorId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Vendor is required");
        }

        if (cmd.vendorBillingEntityId() == null || cmd.vendorBillingEntityId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Vendor billing entity is required");
        }

        if (cmd.orgBillingEntityId() == null || cmd.orgBillingEntityId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Org billing entity is required");
        }

        if (cmd.entries() == null || cmd.entries().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "At least one duty is required");
        }

        for (PurchaseInvoiceEntryCommand entry : cmd.entries()) {
            if (entry.bookingEntryId() == null || entry.bookingEntryId().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Booking entry id is required for every purchase invoice entry");
            }

            if (entry.packageId() == null || entry.packageId().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Package id is required for every purchase invoice entry");
            }
        }
    }

    private void validatePaymentOutCommand(AddPaymentOutCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Payment out command is required");
        }

        if (cmd.purchaseInvoiceId() == null || cmd.purchaseInvoiceId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Purchase invoice id is required");
        }

        if (cmd.paymentMode() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Payment mode is required");
        }

        if (cmd.paidAmount() == null
                || cmd.paidAmount().getAmount() == null
                || cmd.paidAmount().getAmount().signum() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Paid amount must be greater than zero");
        }
    }

    private Client validateVendor(String orgId, String vendorId) {
        Client vendor = clientRepository.findByIdAndOrgId(vendorId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CLIENT_NOT_FOUND, "Vendor not found"));

        if (!Boolean.TRUE.equals(vendor.getSupplier())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Selected client is not marked as supplier/vendor");
        }

        return vendor;
    }

    private void validateVendorBillingEntity(String orgId, String vendorId, String billingEntityId) {
        Client vendor = validateVendor(orgId, vendorId);

        ClientBillingEntity entity = clientBillingEntityRepository.findByIdAndOrgId(billingEntityId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BILLING_ENTITY_NOT_FOUND, "Vendor billing entity not found"));

        if (vendor.getClientBillingEntityIds() == null
                || !vendor.getClientBillingEntityIds().contains(entity.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Vendor billing entity does not belong to selected vendor");
        }
    }

    private void validateOrgBillingEntity(String orgId, String orgBillingEntityId) {
        orgBillingEntityRepository.findByIdAndOrgId(orgBillingEntityId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BILLING_ENTITY_NOT_FOUND, "Org billing entity not found"));
    }

    private void validateDutyForPurchase(BookingEntry duty, String vendorId) {
        if (duty == null) {
            throw new NotFoundException(ErrorCode.DUTY_NOT_FOUND, "Duty not found");
        }

        if (!vendorId.equals(duty.getSupplierId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty is not assigned to selected vendor");
        }

        if (duty.getStatus() != DutyStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only completed duties can be added to purchase invoice");
        }

        if (duty.getBooking() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty booking snapshot missing");
        }

        if (duty.getMasterVehicleId() == null || duty.getMasterVehicleId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty vehicle category is missing");
        }

        if (duty.getPack() == null || duty.getPack().getDutyType() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty package snapshot is missing");
        }
    }

    private com.core.models.Package resolvePurchasePackage(
            String orgId,
            String vendorId,
            BookingEntry duty,
            String packageId
    ) {
        com.core.models.Package pack = packageRepository.findById(packageId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PACKAGE_NOT_FOUND, "Purchase package not found"));

        if (!orgId.equals(pack.getOrgId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Package does not belong to current org");
        }

        if (Boolean.TRUE.equals(pack.getForSales())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Selected package is a sales package; purchase invoice requires vendor package");
        }

        if (!duty.getMasterVehicleId().equals(pack.getMasterVehicleId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Package vehicle does not match duty vehicle");
        }

        if (duty.getPack() == null || duty.getPack().getDutyType() != pack.getDutyType()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Package duty type does not match duty");
        }

        if (pack.getScope() == PackageScope.CLIENT && !vendorId.equals(pack.getClientId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Vendor-specific package does not belong to selected vendor");
        }

        if (pack.getScope() == PackageScope.MASTER && pack.getClientId() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Master package cannot have client/vendor id");
        }

        return pack;
    }

    private void validateVendorInvoiceNumberUnique(String orgId, String vendorId, String vendorInvoiceNumber) {
        if (vendorInvoiceNumber == null || vendorInvoiceNumber.isBlank()) {
            return;
        }

        boolean exists = purchaseInvoiceRepository.existsByOrgIdAndVendorIdAndVendorInvoiceNumberAndStatusNot(
                orgId,
                vendorId,
                vendorInvoiceNumber.trim(),
                PurchaseInvoiceStatus.CANCELLED
        );

        if (exists) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Vendor invoice number already exists for this vendor");
        }
    }

    private void validateNoDuplicateDutiesInRequest(List<String> bookingEntryIds) {
        Set<String> unique = new HashSet<>(bookingEntryIds);

        if (unique.size() != bookingEntryIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Same duty selected multiple times");
        }
    }

    private void validateDutiesNotAlreadyInvoiced(List<String> bookingEntryIds) {
        List<String> alreadyInvoiced = purchaseInvoiceEntryRepository.findActiveBookingEntryIds(bookingEntryIds);

        if (!alreadyInvoiced.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Some duties are already included in purchase invoice: " + alreadyInvoiced);
        }
    }

    private String generatePurchaseInvoiceNumber() {
        return "PI" + Instant.now().toEpochMilli();
    }

    private BigDecimal sumExtraCharges(BookingEntry duty) {
        BigDecimal total = BigDecimal.ZERO;

        if (duty.getCharges() == null || duty.getCharges().isEmpty()) {
            return total;
        }

        for (ExtraCharge charge : duty.getCharges()) {
            total = total.add(amount(charge.getAmount()));
        }

        return total;
    }

    private BigDecimal amount(Money money) {
        return money == null || money.getAmount() == null
                ? BigDecimal.ZERO
                : money.getAmount();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}

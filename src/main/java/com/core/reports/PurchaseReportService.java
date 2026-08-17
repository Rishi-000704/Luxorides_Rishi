package com.core.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.core.dtos.report.PurchaseRegisterRow;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.OrgBillingEntity;
import com.core.models.PurchaseInvoice;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.PurchaseInvoiceStatus;
import com.core.repositories.PurchaseInvoiceRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseReportService {

    private static final Set<PurchaseInvoiceStatus> REPORT_STATUSES =
            EnumSet.of(PurchaseInvoiceStatus.COMPLETED, PurchaseInvoiceStatus.PAID);

    private final PurchaseInvoiceRepository purchaseInvoiceRepository;

    @Transactional(readOnly = true)
    public List<PurchaseRegisterRow> purchaseRegister(
            String orgId,
            String orgBillingEntityId,
            Instant from,
            Instant toExclusive
    ) {
        validateReportRange(orgId, orgBillingEntityId, from, toExclusive);

        return purchaseInvoiceRepository.findReportInvoices(
                        orgId,
                        orgBillingEntityId,
                        REPORT_STATUSES,
                        from,
                        toExclusive
                )
                .stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public String purchaseRegisterCsv(
            String orgId,
            String orgBillingEntityId,
            Instant from,
            Instant toExclusive
    ) {
        List<PurchaseRegisterRow> rows = purchaseRegister(
                orgId,
                orgBillingEntityId,
                from,
                toExclusive
        );

        StringBuilder csv = new StringBuilder();

        csv.append("Purchase Invoice No,");
        csv.append("Vendor Invoice No,");
        csv.append("Vendor Invoice Date,");
        csv.append("Fleetovo Invoice Date,");
        csv.append("Vendor Name,");
        csv.append("Vendor GSTIN,");
        csv.append("Vendor Billing Entity,");
        csv.append("Org Billing Entity,");
        csv.append("Taxable Amount,");
        csv.append("CGST,");
        csv.append("SGST,");
        csv.append("IGST,");
        csv.append("Total GST,");
        csv.append("Grand Total,");
        csv.append("Paid Amount,");
        csv.append("Balance Amount,");
        csv.append("Status");
        csv.append('\n');

        for (PurchaseRegisterRow row : rows) {
            csv.append(esc(row.purchaseInvoiceNumber())).append(',');
            csv.append(esc(row.vendorInvoiceNumber())).append(',');
            csv.append(value(row.vendorInvoiceDate())).append(',');
            csv.append(value(row.invoiceDate())).append(',');
            csv.append(esc(row.vendorName())).append(',');
            csv.append(esc(row.vendorGstin())).append(',');
            csv.append(esc(row.vendorBillingEntity())).append(',');
            csv.append(esc(row.orgBillingEntity())).append(',');
            csv.append(amount(row.taxableAmount())).append(',');
            csv.append(amount(row.cgst())).append(',');
            csv.append(amount(row.sgst())).append(',');
            csv.append(amount(row.igst())).append(',');
            csv.append(amount(row.totalGst())).append(',');
            csv.append(amount(row.grandTotal())).append(',');
            csv.append(amount(row.paidAmount())).append(',');
            csv.append(amount(row.balanceAmount())).append(',');
            csv.append(row.status() == null ? "" : row.status().name());
            csv.append('\n');
        }

        return csv.toString();
    }

    private PurchaseRegisterRow toRow(PurchaseInvoice invoice) {
        GstSnapshot gst = invoice.getGstSnapshot();

        return new PurchaseRegisterRow(
                invoice.getPurchaseInvoiceNumber(),
                invoice.getVendorInvoiceNumber(),
                invoice.getVendorInvoiceDate(),
                invoice.getInvoiceDate(),
                vendorName(invoice.getVendor()),
                vendorGstin(invoice.getVendorBillingEntity()),
                vendorBillingEntityName(invoice.getVendorBillingEntity()),
                orgBillingEntityName(invoice.getOrgBillingEntity()),
                moneyAmount(invoice.getTaxableAmount()),
                gstAmount(gst == null ? null : gst.getCgstAmount()),
                gstAmount(gst == null ? null : gst.getSgstAmount()),
                gstAmount(gst == null ? null : gst.getIgstAmount()),
                gstAmount(gst == null ? null : gst.getTotalTax()),
                moneyAmount(invoice.getGrandTotal()),
                moneyAmount(invoice.getPaidAmount()),
                moneyAmount(invoice.getBalanceAmount()),
                invoice.getStatus()
        );
    }

    private void validateReportRange(
            String orgId,
            String orgBillingEntityId,
            Instant from,
            Instant toExclusive
    ) {
        if (orgId == null || orgId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Org id is required");
        }

        if (orgBillingEntityId == null || orgBillingEntityId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BILLING_ENTITY_NOT_FOUND,
                    "Org billing entity is required for purchase register"
            );
        }

        if (from == null) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD, "Report from date is required");
        }

        if (toExclusive == null) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD, "Report till date is required");
        }

        if (!from.isBefore(toExclusive)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REPORT_PERIOD,
                    "Report from date must be before report till date"
            );
        }
    }

    private String vendorName(Client vendor) {
        if (vendor == null || vendor.getName() == null) {
            return "";
        }

        return vendor.getName().getDisplayName();
    }

    private String vendorGstin(ClientBillingEntity billingEntity) {
        if (billingEntity == null || billingEntity.getGstin() == null) {
            return "";
        }

        return billingEntity.getGstin();
    }

    private String vendorBillingEntityName(ClientBillingEntity billingEntity) {
        if (billingEntity == null || billingEntity.getLegalName() == null) {
            return "";
        }

        return billingEntity.getLegalName();
    }

    private String orgBillingEntityName(OrgBillingEntity billingEntity) {
        if (billingEntity == null || billingEntity.getLegalName() == null) {
            return "";
        }

        return billingEntity.getLegalName();
    }

    private BigDecimal moneyAmount(Money money) {
        if (money == null || money.getAmount() == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        return money.getAmount();
    }

    private BigDecimal gstAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private String amount(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private String value(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private String esc(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}

package com.core.reports;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.config.ReportGenerationProperties;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.ClientBillingEntity;
import com.core.models.Invoice;
import com.core.models.OrgBillingEntity;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.InvoiceStatus;
import com.core.repositories.InvoiceRepository;
import com.core.repositories.OrgBillingEntityRepository;
import com.core.util.DateFormatUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SalesTaxReportService {

    private static final DateTimeFormatter GST_PERIOD_FORMAT = DateTimeFormatter.ofPattern("MMyyyy");
    private static final DateTimeFormatter GST_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final String HSN_CODE = "996601";
    private static final String HSN_DESC =
            "Rental services of road vehicles including buses, coaches, cars, trucks and other motor vehicles, operator";
    private static final String HSN_USER_DESC = "Car Rent Service with Driver";

    private static final Map<String, String> GST_STATE_CODES = Map.ofEntries(
            Map.entry("JAMMU AND KASHMIR", "01"),
            Map.entry("HIMACHAL PRADESH", "02"),
            Map.entry("PUNJAB", "03"),
            Map.entry("CHANDIGARH", "04"),
            Map.entry("UTTARAKHAND", "05"),
            Map.entry("HARYANA", "06"),
            Map.entry("DELHI", "07"),
            Map.entry("RAJASTHAN", "08"),
            Map.entry("UTTAR PRADESH", "09"),
            Map.entry("BIHAR", "10"),
            Map.entry("SIKKIM", "11"),
            Map.entry("ARUNACHAL PRADESH", "12"),
            Map.entry("NAGALAND", "13"),
            Map.entry("MANIPUR", "14"),
            Map.entry("MIZORAM", "15"),
            Map.entry("TRIPURA", "16"),
            Map.entry("MEGHALAYA", "17"),
            Map.entry("ASSAM", "18"),
            Map.entry("WEST BENGAL", "19"),
            Map.entry("JHARKHAND", "20"),
            Map.entry("ODISHA", "21"),
            Map.entry("CHHATTISGARH", "22"),
            Map.entry("MADHYA PRADESH", "23"),
            Map.entry("GUJARAT", "24"),
            Map.entry("DADRA AND NAGAR HAVELI AND DAMAN AND DIU", "26"),
            Map.entry("MAHARASHTRA", "27"),
            Map.entry("ANDHRA PRADESH", "28"),
            Map.entry("KARNATAKA", "29"),
            Map.entry("GOA", "30"),
            Map.entry("LAKSHADWEEP", "31"),
            Map.entry("KERALA", "32"),
            Map.entry("TAMIL NADU", "33"),
            Map.entry("PUDUCHERRY", "34"),
            Map.entry("ANDAMAN AND NICOBAR ISLANDS", "35"),
            Map.entry("TELANGANA", "36"),
            Map.entry("ANDHRA PRADESH NEW", "37"),
            Map.entry("LADAKH", "38")
    );

    private final InvoiceRepository invoiceRepository;
    private final OrgBillingEntityRepository orgBillingEntityRepository;
    private final ObjectMapper objectMapper;
    private final ReportGenerationProperties properties;

    @Transactional(readOnly = true)
    public byte[] generateSalesRegisterCsv(String orgId, String orgBillingEntityId, LocalDate from, LocalDate to) {

        Instant fromInstant = from.atStartOfDay(properties.reportZone()).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(properties.reportZone()).toInstant();

        List<Invoice> invoices = invoiceRepository.fetchInvoicesForReport(
                orgId,
                orgBillingEntityId,
                EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PAID),
                fromInstant,
                toInstant
        );

        List<SalesCsvRow> rows = invoices.stream()
                .sorted(invoiceComparator())
                .map(this::mapToSalesCsvRow)
                .toList();

        return generateCsv(rows);
    }

    @Transactional(readOnly = true)
    public byte[] generateGstr1Json(String orgId, String orgBillingEntityId, LocalDate from, LocalDate to) {

        validateGstPeriod(from, to);

        OrgBillingEntity orgBillingEntity = orgBillingEntityRepository
                .findByIdAndOrgId(orgBillingEntityId, orgId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BILLING_ENTITY_NOT_FOUND,
                        "Org billing entity not found"
                ));

        Instant fromInstant = from.atStartOfDay(properties.reportZone()).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(properties.reportZone()).toInstant();

        List<Invoice> invoices = invoiceRepository.fetchInvoicesForReport(
                        orgId,
                        orgBillingEntityId,
                        EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PAID, InvoiceStatus.CANCELLED),
                        fromInstant,
                        toInstant
                ).stream()
                .sorted(invoiceComparator())
                .toList();

        List<Invoice> activeInvoices = invoices.stream()
                .filter(invoice -> invoice.getStatus() != InvoiceStatus.CANCELLED)
                .toList();

        String orgGstin = cleanGstin(orgBillingEntity.getGstin());
        if (orgGstin.length() != 15) {
            throw new BusinessException(
                    ErrorCode.REPORT_GENERATION_FAILED,
                    "Selected org billing entity does not have a valid GSTIN"
            );
        }

        Map<String, Object> root = new LinkedHashMap<>();

        root.put("gstin", orgGstin);
        root.put("fp", from.format(GST_PERIOD_FORMAT));
        root.put("filing_typ", "M");
        root.put("gt", BigDecimal.ZERO.setScale(1));
        root.put("cur_gt", BigDecimal.ZERO.setScale(1));

        root.put("b2b", buildB2b(activeInvoices));
        root.put("b2cs", buildB2cs(activeInvoices));
        root.put("hsn", buildHsn(activeInvoices));
        root.put("doc_issue", buildDocIssue(invoices));

        root.put("fil_dt", LocalDate.now(properties.reportZone()).format(GST_DATE_FORMAT));

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.REPORT_GENERATION_FAILED, "Failed to generate GSTR-1 JSON report");
        }
    }

    private SalesCsvRow mapToSalesCsvRow(Invoice invoice) {

        String billedTo;
        String address;
        String gstNo;

        if (invoice.getClientBillingEntity() != null) {
            ClientBillingEntity billingEntity = invoice.getClientBillingEntity();

            billedTo = nullSafe(billingEntity.getLegalName());
            address = formattedAddress(billingEntity.getAddress());
            gstNo = nullSafe(billingEntity.getGstin());
        } else {
            billedTo = invoice.getClient() != null && invoice.getClient().getName() != null
                    ? nullSafe(invoice.getClient().getName().getDisplayName())
                    : "";

            address = invoice.getClient() != null
                    ? formattedAddress(invoice.getClient().getAddress())
                    : "";

            gstNo = "-";
        }

        boolean cancelled = invoice.getStatus() == InvoiceStatus.CANCELLED;

        return new SalesCsvRow(
                invoice.getInvoiceNumber(),
                DateFormatUtil.display(invoice.getInvoiceDate()),
                billedTo,
                address,
                gstNo,
                moneyAmount(invoice.getTaxableAmount()),
                cgst(invoice),
                sgst(invoice),
                igst(invoice),
                totalTax(invoice),
                moneyAmount(invoice.getGrandTotal()),
                invoice.getStatus() != null ? invoice.getStatus().name() : "",
                cancelled
        );
    }

    private byte[] generateCsv(List<SalesCsvRow> rows) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            writer.println(
                    "SN,Invoice Id,Billing Date,Billed To,Address,GST No,Taxable Amount,CGST,SGST,IGST,Total Tax,Invoice Amount,Status,Canceled"
            );

            int index = 1;

            for (SalesCsvRow row : rows) {
                writer.println(csvLine(
                        index++,
                        row.invoiceNumber(),
                        row.billingDate(),
                        row.billedTo(),
                        row.address(),
                        row.gstNo(),
                        row.taxableAmount(),
                        row.cgst(),
                        row.sgst(),
                        row.igst(),
                        row.totalTax(),
                        row.netPayable(),
                        row.status(),
                        row.cancelled() ? "Yes" : "No"
                ));
            }

            writer.flush();
        }

        return out.toByteArray();
    }

    private List<Map<String, Object>> buildB2b(List<Invoice> activeInvoices) {

        Map<String, List<Map<String, Object>>> groupedByGstin = new LinkedHashMap<>();

        for (Invoice invoice : activeInvoices) {
            String clientGstin = clientGstin(invoice);

            if (!hasText(clientGstin)) {
                continue;
            }

            groupedByGstin.computeIfAbsent(cleanGstin(clientGstin), key -> new ArrayList<>())
                    .add(buildB2bInvoice(invoice));
        }

        List<Map<String, Object>> b2b = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByGstin.entrySet()) {
            Map<String, Object> customer = new LinkedHashMap<>();
            customer.put("ctin", entry.getKey());
            customer.put("cfs", "N");
            customer.put("inv", entry.getValue());

            b2b.add(customer);
        }

        return b2b;
    }

    private Map<String, Object> buildB2bInvoice(Invoice invoice) {

        Map<String, Object> invoiceJson = new LinkedHashMap<>();

        invoiceJson.put("inum", invoice.getInvoiceNumber());
        invoiceJson.put("idt", gstDate(invoice.getInvoiceDate()));
        invoiceJson.put("val", amount(moneyAmount(invoice.getGrandTotal())));
        invoiceJson.put("pos", resolvePlaceOfSupply(invoice));
        invoiceJson.put("rchrg", "N");
        invoiceJson.put("inv_typ", "R");
        invoiceJson.put("flag", "N");
        invoiceJson.put("cflag", "N");

        Map<String, Object> itemDetail = new LinkedHashMap<>();
        itemDetail.put("rt", gstRate(invoice));
        itemDetail.put("txval", amount(moneyAmount(invoice.getTaxableAmount())));

        if (igst(invoice).signum() > 0) {
            itemDetail.put("iamt", amount(igst(invoice)));
        } else {
            itemDetail.put("camt", amount(cgst(invoice)));
            itemDetail.put("samt", amount(sgst(invoice)));
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("num", 1);
        item.put("itm_det", itemDetail);

        invoiceJson.put("itms", List.of(item));

        return invoiceJson;
    }

    private List<Map<String, Object>> buildB2cs(List<Invoice> activeInvoices) {

        Map<TaxBucket, TaxAggregate> buckets = new LinkedHashMap<>();

        for (Invoice invoice : activeInvoices) {
            String clientGstin = clientGstin(invoice);

            if (hasText(clientGstin)) {
                continue;
            }

            TaxBucket bucket = new TaxBucket(
                    resolvePlaceOfSupply(invoice),
                    gstRate(invoice),
                    supplyType(invoice)
            );

            buckets.computeIfAbsent(bucket, key -> new TaxAggregate()).add(invoice);
        }

        List<Map<String, Object>> b2cs = new ArrayList<>();

        for (Map.Entry<TaxBucket, TaxAggregate> entry : buckets.entrySet()) {
            TaxBucket bucket = entry.getKey();
            TaxAggregate aggregate = entry.getValue();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("typ", "OE");
            row.put("sply_ty", bucket.supplyType());
            row.put("rt", bucket.rate());
            row.put("pos", bucket.pos());
            row.put("txval", amount(aggregate.taxableAmount));
            row.put("iamt", amount(aggregate.igstAmount));
            row.put("camt", amount(aggregate.cgstAmount));
            row.put("samt", amount(aggregate.sgstAmount));
            row.put("csamt", BigDecimal.ZERO.setScale(2));
            row.put("flag", "N");

            b2cs.add(row);
        }

        return b2cs;
    }

    private Map<String, Object> buildHsn(List<Invoice> activeInvoices) {

        Map<Integer, TaxAggregate> b2bAggregates = new LinkedHashMap<>();
        Map<Integer, TaxAggregate> b2cAggregates = new LinkedHashMap<>();

        for (Invoice invoice : activeInvoices) {
            String clientGstin = clientGstin(invoice);
            int rate = gstRate(invoice);

            if (hasText(clientGstin)) {
                b2bAggregates.computeIfAbsent(rate, key -> new TaxAggregate()).add(invoice);
            } else {
                b2cAggregates.computeIfAbsent(rate, key -> new TaxAggregate()).add(invoice);
            }
        }

        Map<String, Object> hsn = new LinkedHashMap<>();

        hsn.put("flag", "N");
        hsn.put("hsn_b2b", buildHsnRows(b2bAggregates));
        hsn.put("hsn_b2c", buildHsnRows(b2cAggregates));

        return hsn;
    }

    private List<Map<String, Object>> buildHsnRows(Map<Integer, TaxAggregate> aggregates) {

        List<Map<String, Object>> rows = new ArrayList<>();

        int index = 1;

        for (Map.Entry<Integer, TaxAggregate> entry : aggregates.entrySet()) {
            TaxAggregate aggregate = entry.getValue();

            if (aggregate.taxableAmount.signum() <= 0) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("num", index++);
            row.put("hsn_sc", HSN_CODE);
            row.put("desc", HSN_DESC);
            row.put("user_desc", HSN_USER_DESC);
            row.put("uqc", "NA");
            row.put("qty", BigDecimal.ZERO);
            row.put("rt", entry.getKey());
            row.put("txval", amount(aggregate.taxableAmount));
            row.put("iamt", amount(aggregate.igstAmount));
            row.put("camt", amount(aggregate.cgstAmount));
            row.put("samt", amount(aggregate.sgstAmount));

            rows.add(row);
        }

        return rows;
    }

    private Map<String, Object> buildDocIssue(List<Invoice> invoices) {

        Map<String, Object> docIssue = new LinkedHashMap<>();
        docIssue.put("flag", "N");

        List<Map<String, Object>> docDetails = new ArrayList<>();

        for (int docNum = 1; docNum <= 12; docNum++) {
            Map<String, Object> docDetail = new LinkedHashMap<>();
            docDetail.put("doc_num", docNum);

            List<Map<String, Object>> docs = new ArrayList<>();

            if (docNum == 1 && !invoices.isEmpty()) {
                int cancelledCount = (int) invoices.stream()
                        .filter(invoice -> invoice.getStatus() == InvoiceStatus.CANCELLED)
                        .count();

                Map<String, Object> invoiceDoc = new LinkedHashMap<>();
                invoiceDoc.put("num", 1);
                invoiceDoc.put("from", invoices.get(0).getInvoiceNumber());
                invoiceDoc.put("to", invoices.get(invoices.size() - 1).getInvoiceNumber());
                invoiceDoc.put("totnum", invoices.size());
                invoiceDoc.put("cancel", cancelledCount);
                invoiceDoc.put("net_issue", invoices.size() - cancelledCount);

                docs.add(invoiceDoc);
            }

            docDetail.put("docs", docs);
            docDetails.add(docDetail);
        }

        docIssue.put("doc_det", docDetails);

        return docIssue;
    }

    private void validateGstPeriod(LocalDate from, LocalDate to) {

        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD, "GST report date range is required");
        }

        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD, "GST report till date cannot be before from date");
        }

        boolean sameMonth = from.getMonth() == to.getMonth() && from.getYear() == to.getYear();
        boolean completeMonth = from.getDayOfMonth() == 1 && to.getDayOfMonth() == to.lengthOfMonth();

        if (!sameMonth || !completeMonth) {
            throw new BusinessException(
                    ErrorCode.INVALID_REPORT_PERIOD,
                    "GSTR-1 JSON must cover one complete GST month"
            );
        }
    }

    private Comparator<Invoice> invoiceComparator() {
        return Comparator
                .comparing(Invoice::getInvoiceDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Invoice::getInvoiceNumber, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private String csvLine(Object... values) {

        List<String> columns = new ArrayList<>();

        for (Object value : values) {
            columns.add(csv(value));
        }

        return String.join(",", columns);
    }

    private String csv(Object value) {

        String text;

        if (value == null) {
            text = "";
        } else if (value instanceof BigDecimal bigDecimal) {
            text = bigDecimal.toPlainString();
        } else {
            text = String.valueOf(value);
        }

        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String clientGstin(Invoice invoice) {

        if (invoice.getClientBillingEntity() == null) {
            return "";
        }

        return cleanGstin(invoice.getClientBillingEntity().getGstin());
    }

    private String cleanGstin(String gstin) {
        return nullSafe(gstin).replaceAll("\\s+", "").toUpperCase();
    }

    private String resolvePlaceOfSupply(Invoice invoice) {

        String placeOfSupply = nullSafe(invoice.getPlaceOfSupply()).trim();

        if (placeOfSupply.matches("\\d{2}")) {
            return placeOfSupply;
        }

        String mappedStateCode = GST_STATE_CODES.get(placeOfSupply.toUpperCase());

        if (mappedStateCode != null) {
            return mappedStateCode;
        }

        String clientGstin = clientGstin(invoice);

        if (clientGstin.length() >= 2) {
            return clientGstin.substring(0, 2);
        }

        return placeOfSupply;
    }

    private String supplyType(Invoice invoice) {
        return igst(invoice).signum() > 0 ? "INTER" : "INTRA";
    }

    private int gstRate(Invoice invoice) {

        GstSnapshot gstSnapshot = invoice.getGstSnapshot();

        if (gstSnapshot == null || gstSnapshot.getGstRate() == null) {
            return 0;
        }

        return gstSnapshot.getGstRate();
    }

    private BigDecimal igst(Invoice invoice) {

        if (invoice.getGstSnapshot() == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        return amount(invoice.getGstSnapshot().getIgstAmount());
    }

    private BigDecimal cgst(Invoice invoice) {

        if (invoice.getGstSnapshot() == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        return amount(invoice.getGstSnapshot().getCgstAmount());
    }

    private BigDecimal sgst(Invoice invoice) {

        if (invoice.getGstSnapshot() == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        return amount(invoice.getGstSnapshot().getSgstAmount());
    }

    private BigDecimal totalTax(Invoice invoice) {

        if (invoice.getGstSnapshot() == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        return amount(invoice.getGstSnapshot().getTotalTax());
    }

    private BigDecimal moneyAmount(Money money) {

        if (money == null || money.getAmount() == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        return amount(money.getAmount());
    }

    private BigDecimal amount(BigDecimal value) {

        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String gstDate(Instant instant) {

        if (instant == null) {
            return "";
        }

        return GST_DATE_FORMAT.format(instant.atZone(properties.reportZone()).toLocalDate());
    }

    private String formattedAddress(DisplayAddress address) {

        if (address == null) {
            return "";
        }

        return nullSafe(address.getFormattedAddress());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record SalesCsvRow(
            String invoiceNumber,
            String billingDate,
            String billedTo,
            String address,
            String gstNo,
            BigDecimal taxableAmount,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal totalTax,
            BigDecimal netPayable,
            String status,
            boolean cancelled
    ) {
    }

    private record TaxBucket(
            String pos,
            int rate,
            String supplyType
    ) {
    }

    private static final class TaxAggregate {

        private BigDecimal taxableAmount = BigDecimal.ZERO.setScale(2);
        private BigDecimal igstAmount = BigDecimal.ZERO.setScale(2);
        private BigDecimal cgstAmount = BigDecimal.ZERO.setScale(2);
        private BigDecimal sgstAmount = BigDecimal.ZERO.setScale(2);

        private void add(Invoice invoice) {
            taxableAmount = taxableAmount.add(safeMoney(invoice.getTaxableAmount()));
            igstAmount = igstAmount.add(safeIgst(invoice));
            cgstAmount = cgstAmount.add(safeCgst(invoice));
            sgstAmount = sgstAmount.add(safeSgst(invoice));
        }

        private static BigDecimal safeMoney(Money money) {

            if (money == null || money.getAmount() == null) {
                return BigDecimal.ZERO.setScale(2);
            }

            return money.getAmount().setScale(2, RoundingMode.HALF_UP);
        }

        private static BigDecimal safeIgst(Invoice invoice) {

            if (invoice.getGstSnapshot() == null || invoice.getGstSnapshot().getIgstAmount() == null) {
                return BigDecimal.ZERO.setScale(2);
            }

            return invoice.getGstSnapshot().getIgstAmount().setScale(2, RoundingMode.HALF_UP);
        }

        private static BigDecimal safeCgst(Invoice invoice) {

            if (invoice.getGstSnapshot() == null || invoice.getGstSnapshot().getCgstAmount() == null) {
                return BigDecimal.ZERO.setScale(2);
            }

            return invoice.getGstSnapshot().getCgstAmount().setScale(2, RoundingMode.HALF_UP);
        }

        private static BigDecimal safeSgst(Invoice invoice) {

            if (invoice.getGstSnapshot() == null || invoice.getGstSnapshot().getSgstAmount() == null) {
                return BigDecimal.ZERO.setScale(2);
            }

            return invoice.getGstSnapshot().getSgstAmount().setScale(2, RoundingMode.HALF_UP);
        }
    }
}
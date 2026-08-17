package com.core.reports.generation;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.ReportRequest;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportType;
import com.core.reports.SalesTaxReportService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesRegisterReportGenerator implements ReportGenerator {

    private final SalesTaxReportService salesTaxReportService;

    @Override
    public boolean supports(ReportType reportType, ReportFormat format) {
        boolean supportedType = reportType == ReportType.SALES_REGISTER
                || reportType == ReportType.SALES_GST_REGISTER;

        return supportedType && format == ReportFormat.CSV;
    }

    @Override
    public GeneratedReportFile generate(ReportRequest request) {
        if (request.getOrgBillingEntityId() == null || request.getOrgBillingEntityId().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BILLING_ENTITY_NOT_FOUND,
                    "Org billing entity is required for sales report"
            );
        }

        byte[] csv = salesTaxReportService.generateSalesRegisterCsv(
                request.getOrgId(),
                request.getOrgBillingEntityId(),
                request.getPeriodFrom(),
                request.getPeriodTo()
        );

        return new GeneratedReportFile(
                csv,
                buildFileName(request),
                "text/csv"
        );
    }

    private String buildFileName(ReportRequest request) {
        String prefix = request.getReportType() == ReportType.SALES_GST_REGISTER
                ? "sales-gst-register"
                : "sales-register";

        return prefix + "-" + request.getPeriodFrom() + "-to-" + request.getPeriodTo() + ".csv";
    }
}

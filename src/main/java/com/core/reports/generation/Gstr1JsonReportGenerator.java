package com.core.reports.generation;

import java.time.format.DateTimeFormatter;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.ReportRequest;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportType;
import com.core.reports.SalesTaxReportService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Gstr1JsonReportGenerator implements ReportGenerator {

    private static final DateTimeFormatter GST_PERIOD_FORMAT = DateTimeFormatter.ofPattern("MMyyyy");

    private final SalesTaxReportService salesTaxReportService;

    @Override
    public boolean supports(ReportType reportType, ReportFormat format) {
        return reportType == ReportType.GSTR1 && format == ReportFormat.JSON;
    }

    @Override
    public GeneratedReportFile generate(ReportRequest request) {
        if (request.getOrgBillingEntityId() == null || request.getOrgBillingEntityId().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BILLING_ENTITY_NOT_FOUND,
                    "Org billing entity is required for GSTR-1 report"
            );
        }

        byte[] json = salesTaxReportService.generateGstr1Json(
                request.getOrgId(),
                request.getOrgBillingEntityId(),
                request.getPeriodFrom(),
                request.getPeriodTo()
        );

        return new GeneratedReportFile(
                json,
                "gstr1-" + request.getPeriodFrom().format(GST_PERIOD_FORMAT) + ".json",
                MediaType.APPLICATION_JSON_VALUE
        );
    }
}

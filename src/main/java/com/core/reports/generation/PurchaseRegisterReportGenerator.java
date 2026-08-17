package com.core.reports.generation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.core.config.ReportGenerationProperties;
import com.core.dtos.report.PurchaseRegisterRow;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.ReportRequest;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportType;
import com.core.reports.PurchaseReportService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PurchaseRegisterReportGenerator implements ReportGenerator {

    private final PurchaseReportService purchaseReportService;
    private final ObjectMapper objectMapper;
    private final ReportGenerationProperties properties;

    @Override
    public boolean supports(ReportType reportType, ReportFormat format) {
        return reportType == ReportType.PURCHASE_REGISTER
                && (format == ReportFormat.CSV || format == ReportFormat.JSON);
    }

    @Override
    public GeneratedReportFile generate(ReportRequest request) {
        if (request.getOrgBillingEntityId() == null || request.getOrgBillingEntityId().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BILLING_ENTITY_NOT_FOUND,
                    "Org billing entity is required for purchase register"
            );
        }

        return switch (request.getFormat()) {
            case CSV -> generateCsv(request);
            case JSON -> generateJson(request);
            case PDF -> throw new BusinessException(
                    ErrorCode.REPORT_FORMAT_COMING_SOON,
                    "PDF format for Purchase Register is coming soon"
            );
        };
    }

    private GeneratedReportFile generateCsv(ReportRequest request) {
        String csv = purchaseReportService.purchaseRegisterCsv(
                request.getOrgId(),
                request.getOrgBillingEntityId(),
                startOfDay(request.getPeriodFrom()),
                startOfNextDay(request.getPeriodTo())
        );

        return new GeneratedReportFile(
                csv.getBytes(StandardCharsets.UTF_8),
                "purchase-register-" + request.getPeriodFrom() + "-to-" + request.getPeriodTo() + ".csv",
                "text/csv"
        );
    }

    private GeneratedReportFile generateJson(ReportRequest request) {
        try {
            List<PurchaseRegisterRow> rows = purchaseReportService.purchaseRegister(
                    request.getOrgId(),
                    request.getOrgBillingEntityId(),
                    startOfDay(request.getPeriodFrom()),
                    startOfNextDay(request.getPeriodTo())
            );

            byte[] json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(rows);

            return new GeneratedReportFile(
                    json,
                    "purchase-register-" + request.getPeriodFrom() + "-to-" + request.getPeriodTo() + ".json",
                    MediaType.APPLICATION_JSON_VALUE
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.REPORT_GENERATION_FAILED,
                    "Failed to generate Purchase Register JSON"
            );
        }
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(reportZone()).toInstant();
    }

    private Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(reportZone()).toInstant();
    }

    private ZoneId reportZone() {
        return properties.reportZone();
    }
}

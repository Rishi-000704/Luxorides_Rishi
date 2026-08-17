package com.core.reports.generation;

import java.util.List;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.ReportRequest;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportGeneratorRegistry {

    private final List<ReportGenerator> generators;

    public boolean supports(ReportType reportType, ReportFormat format) {
        return generators.stream().anyMatch(generator -> generator.supports(reportType, format));
    }

    public GeneratedReportFile generate(ReportRequest request) {
        return generators.stream()
                .filter(generator -> generator.supports(request))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_GENERATOR_NOT_FOUND,
                        "No report generator is available for "
                                + request.getReportType()
                                + " in "
                                + request.getFormat()
                                + " format"
                ))
                .generate(request);
    }
}

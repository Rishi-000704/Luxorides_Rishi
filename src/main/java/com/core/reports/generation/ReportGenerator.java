package com.core.reports.generation;

import com.core.models.ReportRequest;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportType;

public interface ReportGenerator {

    boolean supports(ReportType reportType, ReportFormat format);

    default boolean supports(ReportRequest request) {
        return request != null && supports(request.getReportType(), request.getFormat());
    }

    GeneratedReportFile generate(ReportRequest request);
}

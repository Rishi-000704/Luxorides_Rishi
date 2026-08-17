package com.core.events;

import java.time.LocalDate;

import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportType;

public record ReportRequestedEvent(
        String reportRequestId,
        String orgId,
        String requestedBy,
        ReportCategory category,
        ReportType reportType,
        ReportFormat format,
        LocalDate periodFrom,
        LocalDate periodTo
) {
}
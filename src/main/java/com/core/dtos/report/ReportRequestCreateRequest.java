package com.core.dtos.report;

import java.time.LocalDate;
import java.util.Map;

import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportPartyType;
import com.core.models.enums.ReportType;

import jakarta.validation.constraints.NotNull;

public record ReportRequestCreateRequest(
        @NotNull(message = "Report category is required")
        ReportCategory category,

        @NotNull(message = "Report type is required")
        ReportType reportType,

        @NotNull(message = "Report format is required")
        ReportFormat format,

        @NotNull(message = "Period from is required")
        LocalDate periodFrom,

        @NotNull(message = "Period to is required")
        LocalDate periodTo,

        String orgBillingEntityId,

        ReportPartyType partyType,

        String partyId,

        Map<String, Object> filters
) {
}
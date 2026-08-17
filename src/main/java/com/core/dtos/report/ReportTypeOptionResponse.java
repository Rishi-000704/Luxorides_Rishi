package com.core.dtos.report;

import java.util.Set;

import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportPartyType;
import com.core.models.enums.ReportPeriodRule;
import com.core.models.enums.ReportType;

public record ReportTypeOptionResponse(
        ReportCategory category,
        ReportType reportType,
        String label,
        Boolean available,
        String comingSoonMessage,
        Set<ReportFormat> supportedFormats,
        Boolean requiresPeriod,
        ReportPeriodRule periodRule,
        Boolean requiresOrgBillingEntity,
        Boolean requiresParty,
        Set<ReportPartyType> allowedPartyTypes
) {
}

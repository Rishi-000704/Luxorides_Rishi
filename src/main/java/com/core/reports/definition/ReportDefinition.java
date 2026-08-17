package com.core.reports.definition;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportPartyType;
import com.core.models.enums.ReportPeriodRule;
import com.core.models.enums.ReportType;

public record ReportDefinition(
        ReportCategory category,
        ReportType reportType,
        String label,
        boolean available,
        Set<ReportFormat> supportedFormats,
        ReportPeriodRule periodRule,
        boolean requiresOrgBillingEntity,
        boolean requiresParty,
        Set<ReportPartyType> allowedPartyTypes,
        String comingSoonMessage
) {
    public ReportDefinition {
        supportedFormats = immutableEnumSet(supportedFormats);
        allowedPartyTypes = immutableEnumSet(allowedPartyTypes);
    }

    public boolean supportsFormat(ReportFormat format) {
        return format != null && supportedFormats.contains(format);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
}

package com.core.dtos.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportPartyType;
import com.core.models.enums.ReportStatus;
import com.core.models.enums.ReportType;

public record ReportRequestResponse(
        String id,
        String orgId,
        String requestedBy,
        ReportCategory category,
        ReportType reportType,
        ReportFormat format,
        ReportStatus status,
        LocalDate periodFrom,
        LocalDate periodTo,
        String orgBillingEntityId,
        ReportPartyType partyType,
        String partyId,
        Map<String, Object> filters,
        String displayName,
        String fileName,
        String originalFileName,
        String contentType,
        Long fileSize,
        String errorMessage,
        Integer attemptCount,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt,
        Integer downloadCount,
        Instant lastDownloadedAt,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Boolean downloadable,
        String downloadUrl,
        Boolean refreshable,
        String statusMessage
) {
}

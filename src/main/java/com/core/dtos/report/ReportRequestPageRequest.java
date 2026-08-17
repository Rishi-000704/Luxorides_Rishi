package com.core.dtos.report;

import java.util.Set;

import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportStatus;
import com.core.models.enums.ReportType;
import org.springframework.data.domain.Sort;

public record ReportRequestPageRequest(
        ReportStatus status,
        ReportCategory category,
        ReportType reportType,
        ReportFormat format,
        String searchstr,
        Integer page,
        Integer size,
        String sortBy,
        Sort.Direction direction
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT_BY = "createdAt";
    private static final Sort.Direction DEFAULT_DIRECTION = Sort.Direction.DESC;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "requestedAt",
            "startedAt",
            "completedAt",
            "expiresAt",
            "status",
            "category",
            "reportType",
            "format",
            "periodFrom",
            "periodTo",
            "displayName"
    );

    public ReportRequestPageRequest {
        searchstr = searchstr == null ? "" : searchstr.trim();
        page = page == null || page < 0 ? DEFAULT_PAGE : page;
        size = normalizeSize(size);
        sortBy = normalizeSortBy(sortBy);
        direction = direction == null ? DEFAULT_DIRECTION : direction;
    }

    private static int normalizeSize(Integer requestedSize) {
        if (requestedSize == null || requestedSize <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requestedSize, MAX_SIZE);
    }

    private static String normalizeSortBy(String requestedSortBy) {
        if (requestedSortBy == null || requestedSortBy.isBlank()) {
            return DEFAULT_SORT_BY;
        }

        String cleaned = requestedSortBy.trim();
        return ALLOWED_SORT_FIELDS.contains(cleaned) ? cleaned : DEFAULT_SORT_BY;
    }
}
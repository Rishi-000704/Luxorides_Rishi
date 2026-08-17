package com.core.dtos.booking;

import org.springframework.data.domain.Sort;

import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;

public record DutyPageRequest(
        DutyStatus status,
        BookingStatus bookingStatus,
        String bookingId,
        String searchstr,
        Integer page,
        Integer size,
        String sortBy,
        Sort.Direction direction
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT_BY = "reportingTime";
    private static final Sort.Direction DEFAULT_DIRECTION = Sort.Direction.DESC;

    public DutyPageRequest {
        bookingId = bookingId == null ? "" : bookingId.trim();
        searchstr = searchstr == null ? "" : searchstr.trim();
        page = page == null || page < 0 ? DEFAULT_PAGE : page;
        size = normalizeSize(size);
        sortBy = sortBy == null || sortBy.isBlank() ? DEFAULT_SORT_BY : sortBy.trim();
        direction = direction == null ? DEFAULT_DIRECTION : direction;
    }

    public static DutyPageRequest defaults() {
        return new DutyPageRequest(null, null, "", "", DEFAULT_PAGE, DEFAULT_SIZE, DEFAULT_SORT_BY, DEFAULT_DIRECTION);
    }

    private static int normalizeSize(Integer requestedSize) {
        if (requestedSize == null || requestedSize <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requestedSize, MAX_SIZE);
    }
}
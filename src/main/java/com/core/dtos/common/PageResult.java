package com.core.dtos.common;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty,
        String sortBy,
        Sort.Direction direction
) {
    public static <T> PageResult<T> from(Page<T> page, String sortBy, Sort.Direction direction) {
        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty(),
                sortBy,
                direction
        );
    }
}
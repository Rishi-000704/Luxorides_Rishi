package com.core.dataexchange.dto;

public record DataExchangeImportIssue(
        long rowNumber,
        String column,
        String code,
        String message
) {
}

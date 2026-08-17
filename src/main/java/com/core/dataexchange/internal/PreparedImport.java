package com.core.dataexchange.internal;

import java.util.List;

import com.core.dataexchange.dto.DataExchangeImportIssue;

public record PreparedImport<T>(
        List<T> commands,
        int totalRows,
        int createCount,
        int updateCount,
        int unchangedCount,
        List<DataExchangeImportIssue> errors
) {
    public boolean valid() {
        return errors == null || errors.isEmpty();
    }
}

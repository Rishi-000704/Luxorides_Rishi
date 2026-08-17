package com.core.dataexchange.dto;

import java.util.List;

import com.core.dataexchange.model.DataExchangeResource;

public record DataExchangeImportResult(
        DataExchangeResource resource,
        boolean valid,
        boolean committed,
        int totalRows,
        int createCount,
        int updateCount,
        int unchangedCount,
        int errorCount,
        List<DataExchangeImportIssue> errors,
        String message
) {
    public static DataExchangeImportResult of(
            DataExchangeResource resource,
            boolean committed,
            int totalRows,
            int createCount,
            int updateCount,
            int unchangedCount,
            List<DataExchangeImportIssue> errors
    ) {
        boolean valid = errors == null || errors.isEmpty();
        String message;
        if (!valid) {
            message = "CSV contains validation errors. No database changes were applied.";
        } else if (committed) {
            message = "CSV imported successfully.";
        } else {
            message = "CSV validation completed successfully. The file can be imported.";
        }
        return new DataExchangeImportResult(
                resource,
                valid,
                committed,
                totalRows,
                createCount,
                updateCount,
                unchangedCount,
                errors == null ? 0 : errors.size(),
                errors == null ? List.of() : List.copyOf(errors),
                message
        );
    }
}

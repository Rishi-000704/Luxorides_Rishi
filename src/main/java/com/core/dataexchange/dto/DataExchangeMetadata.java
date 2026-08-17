package com.core.dataexchange.dto;

import java.util.List;

import com.core.dataexchange.model.DataExchangeResource;

public record DataExchangeMetadata(
        DataExchangeResource resource,
        String path,
        String displayName,
        String schemaVersion,
        int maxRows,
        long maxFileSizeBytes,
        List<DataExchangeColumn> columns,
        List<String> notes
) {
}

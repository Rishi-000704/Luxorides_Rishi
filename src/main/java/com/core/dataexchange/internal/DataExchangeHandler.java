package com.core.dataexchange.internal;

import java.util.List;
import java.util.Map;

import com.core.dataexchange.dto.DataExchangeColumn;
import com.core.dataexchange.dto.DataExchangeImportResult;
import com.core.dataexchange.model.DataExchangeResource;

public interface DataExchangeHandler {

    DataExchangeResource resource();

    String displayName();

    List<DataExchangeColumn> columns();

    List<String> notes();

    List<Map<String, String>> exportRows(String orgId);

    DataExchangeImportResult validate(
            List<CsvRow> rows,
            String orgId
    );

    DataExchangeImportResult importRows(
            List<CsvRow> rows,
            String orgId,
            DataExchangeImportAuthorization authorization
    );
}
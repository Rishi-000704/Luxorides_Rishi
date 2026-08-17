package com.core.dataexchange.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.core.dataexchange.dto.DataExchangeColumn;
import com.core.dataexchange.dto.DataExchangeImportIssue;
import com.core.dataexchange.dto.DataExchangeImportResult;

public abstract class AbstractDataExchangeHandler<T> implements DataExchangeHandler {

    @Override
    public DataExchangeImportResult validate(List<CsvRow> rows, String orgId) {
        PreparedImport<T> prepared = prepare(rows, orgId);
        return result(prepared, false);
    }

    @Override
    public DataExchangeImportResult importRows(
            List<CsvRow> rows,
            String orgId,
            DataExchangeImportAuthorization authorization
    ) {
        PreparedImport<T> prepared = prepare(rows, orgId);

        if (!prepared.valid()) {
            return result(prepared, false);
        }

        /*
         * Authorize the exact create/update operations represented by this
         * prepared import before applying any mutation.
         */
        authorization.authorize(
                prepared.createCount(),
                prepared.updateCount()
        );

        apply(prepared.commands(), orgId);

        return result(prepared, true);
    }

    protected abstract PreparedImport<T> prepare(List<CsvRow> rows, String orgId);

    protected abstract void apply(List<T> commands, String orgId);

    protected DataExchangeImportResult result(PreparedImport<T> prepared, boolean committed) {
        return DataExchangeImportResult.of(
                resource(),
                committed,
                prepared.totalRows(),
                prepared.createCount(),
                prepared.updateCount(),
                prepared.unchangedCount(),
                prepared.errors()
        );
    }

    protected List<String> headers() {
        return columns().stream().map(DataExchangeColumn::name).toList();
    }

    protected Map<String, String> row(Object... keyValues) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            String key = (String) keyValues[index];
            Object value = keyValues[index + 1];
            row.put(key, value == null ? "" : String.valueOf(value));
        }
        return row;
    }

    protected String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected boolean same(Object left, Object right) {
        return Objects.equals(left, right);
    }

    protected void validateDuplicateRecordId(String id, long rowNumber, Map<String, Long> seenIds,
                                             List<DataExchangeImportIssue> errors) {
        if (id == null) {
            return;
        }
        Long previous = seenIds.putIfAbsent(id, rowNumber);
        if (previous != null) {
            errors.add(new DataExchangeImportIssue(
                    rowNumber,
                    "record_id",
                    "DUPLICATE_RECORD_ID",
                    "record_id is already used at CSV row " + previous + "."
            ));
        }
    }

    protected <E extends Enum<E>> List<String> enumValues(Class<E> enumType) {
        List<String> values = new ArrayList<>();
        for (E value : enumType.getEnumConstants()) {
            values.add(value.name());
        }
        return values;
    }
}

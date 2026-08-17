package com.core.dataexchange.internal;

import java.util.Map;

public record CsvRow(long rowNumber, Map<String, String> values) {
    public String value(String column) {
        return values.get(column);
    }
}

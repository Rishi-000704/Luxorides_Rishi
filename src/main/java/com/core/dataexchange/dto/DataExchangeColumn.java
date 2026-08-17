package com.core.dataexchange.dto;

import java.util.List;

import com.core.dataexchange.model.DataExchangeColumnType;

public record DataExchangeColumn(
        String name,
        String label,
        DataExchangeColumnType type,
        boolean requiredForCreate,
        boolean editable,
        String description,
        List<String> allowedValues
) {
    public static DataExchangeColumn text(String name, String label, boolean required, String description) {
        return new DataExchangeColumn(name, label, DataExchangeColumnType.TEXT, required, true, description, List.of());
    }

    public static DataExchangeColumn identifier(String name, String label, boolean required, String description) {
        return new DataExchangeColumn(name, label, DataExchangeColumnType.IDENTIFIER, required, true, description, List.of());
    }

    public static DataExchangeColumn phone(String name, String label, boolean required, String description) {
        return new DataExchangeColumn(name, label, DataExchangeColumnType.PHONE, required, true, description, List.of());
    }

    public static DataExchangeColumn email(String name, String label, boolean required, String description) {
        return new DataExchangeColumn(name, label, DataExchangeColumnType.EMAIL, required, true, description, List.of());
    }

    public static DataExchangeColumn integer(String name, String label, boolean required, String description) {
        return new DataExchangeColumn(name, label, DataExchangeColumnType.INTEGER, required, true, description, List.of());
    }

    public static DataExchangeColumn decimal(String name, String label, boolean required, String description) {
        return new DataExchangeColumn(name, label, DataExchangeColumnType.DECIMAL, required, true, description, List.of());
    }

    public static DataExchangeColumn bool(String name, String label, boolean required, String description) {
        return new DataExchangeColumn(name, label, DataExchangeColumnType.BOOLEAN, required, true, description,
                List.of("TRUE", "FALSE"));
    }

    public static DataExchangeColumn enumeration(String name, String label, boolean required,
                                                  String description, List<String> allowedValues) {
        return new DataExchangeColumn(name, label, DataExchangeColumnType.ENUM, required, true,
                description, allowedValues);
    }
}

package com.core.dataexchange.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.core.dataexchange.dto.DataExchangeImportIssue;
import com.core.util.PhoneNumberNormalizer;

public final class RowValidation {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CsvRow row;
    private final List<DataExchangeImportIssue> errors;

    public RowValidation(CsvRow row, List<DataExchangeImportIssue> errors) {
        this.row = row;
        this.errors = errors;
    }

    public String optional(String column) {
        return optional(column, null);
    }

    public String optional(String column, Integer maxLength) {
        String value = row.value(column);
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        validateLength(column, value, maxLength);
        return value;
    }

    public String required(String column) {
        return required(column, null);
    }

    public String required(String column, Integer maxLength) {
        String value = optional(column, maxLength);
        if (value == null) {
            issue(column, "REQUIRED", "Value is required.");
        }
        return value;
    }

    public String email(String column, boolean required) {
        return email(column, required, null);
    }

    public String email(String column, boolean required, Integer maxLength) {
        String value = required ? required(column, maxLength) : optional(column, maxLength);
        if (value != null && !EMAIL_PATTERN.matcher(value).matches()) {
            issue(column, "INVALID_EMAIL", "Enter a valid email address.");
        }
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    public String phone(String column, boolean required) {
        String value = required ? required(column) : optional(column);
        if (value == null) {
            return null;
        }
        try {
            return PhoneNumberNormalizer.normalize(value);
        } catch (RuntimeException ex) {
            issue(column, "INVALID_PHONE", ex.getMessage() == null ? "Invalid phone number." : ex.getMessage());
            return value;
        }
    }

    public Integer integer(String column, boolean required, Integer min) {
        String value = required ? required(column) : optional(column);
        if (value == null) {
            return null;
        }
        try {
            Integer result = Integer.valueOf(value);
            if (min != null && result < min) {
                issue(column, "OUT_OF_RANGE", "Value must be at least " + min + ".");
            }
            return result;
        } catch (NumberFormatException ex) {
            issue(column, "INVALID_INTEGER", "Enter a valid whole number.");
            return null;
        }
    }

    public Double decimalDouble(String column, boolean required) {
        String value = required ? required(column) : optional(column);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            issue(column, "INVALID_DECIMAL", "Enter a valid decimal number.");
            return null;
        }
    }

    public BigDecimal decimal(String column, boolean required, BigDecimal min) {
        String value = required ? required(column) : optional(column);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal result = new BigDecimal(value);
            if (min != null && result.compareTo(min) < 0) {
                issue(column, "OUT_OF_RANGE", "Value must be at least " + min.toPlainString() + ".");
            }
            return result;
        } catch (NumberFormatException ex) {
            issue(column, "INVALID_DECIMAL", "Enter a valid decimal number.");
            return null;
        }
    }

    public Boolean bool(String column, boolean required) {
        String value = required ? required(column) : optional(column);
        if (value == null) {
            return null;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equals("1")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no") || value.equals("0")) {
            return false;
        }
        issue(column, "INVALID_BOOLEAN", "Use TRUE or FALSE.");
        return null;
    }

    public <E extends Enum<E>> E enumeration(String column, boolean required, Class<E> enumType) {
        String value = required ? required(column) : optional(column);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            List<String> allowed = new ArrayList<>();
            for (E item : enumType.getEnumConstants()) {
                allowed.add(item.name());
            }
            issue(column, "INVALID_ENUM", "Allowed values: " + String.join(", ", allowed));
            return null;
        }
    }

    public <T> T parse(String column, boolean required, Function<String, T> parser, String message) {
        String value = required ? required(column) : optional(column);
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (RuntimeException ex) {
            issue(column, "INVALID_VALUE", message);
            return null;
        }
    }

    public void issue(String column, String code, String message) {
        errors.add(new DataExchangeImportIssue(row.rowNumber(), column, code, message));
    }

    private void validateLength(String column, String value, Integer maxLength) {
        if (maxLength != null && value.length() > maxLength) {
            issue(column, "TOO_LONG", "Value must not exceed " + maxLength + " characters.");
        }
    }
}

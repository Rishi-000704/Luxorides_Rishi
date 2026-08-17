package com.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AmountToWordsUtil {

    private AmountToWordsUtil() {
        // utility class
    }

    private static final String[] UNITS = {
            "", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen",
            "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    };

    private static final String[] TENS = {
            "", "", "twenty", "thirty", "forty",
            "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    /**
     * Converts a BigDecimal amount into words using Indian numbering system.
     * Decimal part (if any) is ignored.
     *
     * Examples:
     *  15266        -> Fifteen thousand two hundred sixty six only
     *  123456.78    -> One lakh twenty three thousand four hundred fifty six only
     *  10000000     -> One crore only
     */
    public static String convert(BigDecimal amount) {

        if (amount == null) {
            return "Zero only";
        }

        // Ignore decimal part completely (accounting rule)
        long value = amount.setScale(0, RoundingMode.DOWN).longValue();

        if (value == 0) {
            return "Zero only";
        }

        String words = convertIndian(value).trim();
        return capitalize(words) + " only";
    }

    /* ======================================================
     * Indian Number System Logic
     * ====================================================== */

    private static String convertIndian(long number) {

        if (number < 20) {
            return UNITS[(int) number];
        }

        if (number < 100) {
            return TENS[(int) (number / 10)]
                    + ((number % 10 != 0) ? " " + UNITS[(int) (number % 10)] : "");
        }

        if (number < 1000) {
            return UNITS[(int) (number / 100)] + " hundred"
                    + ((number % 100 != 0) ? " " + convertIndian(number % 100) : "");
        }

        if (number < 100_000) { // Thousand
            return convertIndian(number / 1000) + " thousand"
                    + ((number % 1000 != 0) ? " " + convertIndian(number % 1000) : "");
        }

        if (number < 10_000_000) { // Lakh
            return convertIndian(number / 100_000) + " lakh"
                    + ((number % 100_000 != 0) ? " " + convertIndian(number % 100_000) : "");
        }

        // Crore and above
        return convertIndian(number / 10_000_000) + " crore"
                + ((number % 10_000_000 != 0) ? " " + convertIndian(number % 10_000_000) : "");
    }

    private static String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }
}

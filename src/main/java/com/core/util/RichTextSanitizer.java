package com.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

public final class RichTextSanitizer {

    private static final Set<String> ALLOWED_TAGS =
            Set.of(
                    "p",
                    "div",
                    "br",
                    "span",

                    "strong",
                    "b",

                    "em",
                    "i",

                    "u",

                    "s",
                    "strike",

                    "sub",
                    "sup",

                    "h1",
                    "h2",
                    "h3",
                    "h4",
                    "h5",
                    "h6",

                    "blockquote",

                    "ol",
                    "ul",
                    "li"
            );

    private static final Pattern ALIGN_CLASS =
            Pattern.compile(
                    "^ql-align-(left|center|right|justify)$"
            );

    private static final Pattern INDENT_CLASS =
            Pattern.compile(
                    "^ql-indent-[1-8]$"
            );

    private static final Pattern SIZE_CLASS =
            Pattern.compile(
                    "^ql-size-(small|normal|large|huge)$"
            );

    private static final Pattern FONT_CLASS =
            Pattern.compile(
                    "^ql-font-(sans-serif|serif|monospace)$"
            );

    private static final Set<String> SIMPLE_ALLOWED_CLASSES =
            Set.of(
                    "ql-direction-rtl"
            );

    private static final Pattern HEX_COLOR =
            Pattern.compile(
                    "^#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$"
            );

    private static final Pattern RGB_COLOR =
            Pattern.compile(
                    "^rgb\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Set<String> NAMED_COLORS =
            Set.of(
                    "black",
                    "white",
                    "red",
                    "green",
                    "blue",
                    "yellow",
                    "gray",
                    "grey",
                    "orange",
                    "purple",
                    "pink",
                    "brown",
                    "cyan",
                    "magenta",
                    "transparent"
            );

    private static final Set<String> ALLOWED_DATA_LIST_VALUES =
            Set.of(
                    "ordered",
                    "bullet",
                    "checked",
                    "unchecked"
            );

    private static final Safelist INVOICE_TERMS_SAFELIST =
            createSafelist();

    private RichTextSanitizer() {
    }

    private static Safelist createSafelist() {

        Safelist safelist =
                Safelist.none();

        for (String tag : ALLOWED_TAGS) {

            safelist.addTags(tag);

            /*
             * Quill uses CSS classes for:
             *
             * alignment
             * indentation
             * direction
             * font
             * size
             *
             * Quill commonly uses inline style for:
             *
             * text color
             * background color
             */
            safelist.addAttributes(
                    tag,
                    "class",
                    "style",
                    "dir"
            );
        }

        /*
         * Quill 2 list representation can use:
         *
         * <li data-list="bullet">
         * <li data-list="ordered">
         */
        safelist.addAttributes(
                "li",
                "data-list"
        );

        return safelist;
    }

    public static String sanitizeInvoiceTerms(
            String html
    ) {

        if (
                html == null
                        || html.isBlank()
        ) {
            return null;
        }

        Document.OutputSettings outputSettings =
                new Document.OutputSettings()
                        .prettyPrint(false)
                        .syntax(
                                Document.OutputSettings.Syntax.xml
                        );

        String cleaned =
                Jsoup.clean(
                        html,
                        "",
                        INVOICE_TERMS_SAFELIST,
                        outputSettings
                );

        if (
                cleaned == null
                        || cleaned.isBlank()
        ) {
            return null;
        }

        Document document =
                Jsoup.parseBodyFragment(cleaned);

        document
                .outputSettings()
                .prettyPrint(false)
                .syntax(
                        Document.OutputSettings.Syntax.xml
                );

        /*
         * Quill's internal list UI markers are editor-only.
         * They must not become part of the invoice PDF.
         */
        document
                .select(".ql-ui")
                .remove();

        for (
                Element element
                : document.body().getAllElements()
        ) {

            if (element == document.body()) {
                continue;
            }

            sanitizeClasses(element);
            sanitizeInlineStyle(element);
            sanitizeDirection(element);
            sanitizeListType(element);
        }

        String result =
                document
                        .body()
                        .html()
                        .trim();

        if (isEffectivelyEmpty(result)) {
            return null;
        }

        return result;
    }

    private static void sanitizeClasses(
            Element element
    ) {

        String rawClass =
                element.attr("class");

        if (
                rawClass == null
                        || rawClass.isBlank()
        ) {

            element.removeAttr("class");
            return;
        }

        List<String> allowedClasses =
                new ArrayList<>();

        for (
                String cssClass
                : rawClass.trim().split("\\s+")
        ) {

            if (isAllowedClass(cssClass)) {
                allowedClasses.add(cssClass);
            }
        }

        if (allowedClasses.isEmpty()) {

            element.removeAttr("class");

        } else {

            element.attr(
                    "class",
                    String.join(
                            " ",
                            allowedClasses
                    )
            );
        }
    }

    private static boolean isAllowedClass(
            String cssClass
    ) {

        if (
                cssClass == null
                        || cssClass.isBlank()
        ) {
            return false;
        }

        if (
                SIMPLE_ALLOWED_CLASSES
                        .contains(cssClass)
        ) {
            return true;
        }

        if (
                ALIGN_CLASS
                        .matcher(cssClass)
                        .matches()
        ) {
            return true;
        }

        if (
                INDENT_CLASS
                        .matcher(cssClass)
                        .matches()
        ) {
            return true;
        }

        if (
                SIZE_CLASS
                        .matcher(cssClass)
                        .matches()
        ) {
            return true;
        }

        return FONT_CLASS
                .matcher(cssClass)
                .matches();
    }

    private static void sanitizeInlineStyle(
            Element element
    ) {

        String rawStyle =
                element.attr("style");

        if (
                rawStyle == null
                        || rawStyle.isBlank()
        ) {

            element.removeAttr("style");
            return;
        }

        Map<String, String> safeStyles =
                new LinkedHashMap<>();

        String[] declarations =
                rawStyle.split(";");

        for (
                String declaration
                : declarations
        ) {

            if (
                    declaration == null
                            || declaration.isBlank()
            ) {
                continue;
            }

            int colon =
                    declaration.indexOf(':');

            if (colon <= 0) {
                continue;
            }

            String property =
                    declaration
                            .substring(0, colon)
                            .trim()
                            .toLowerCase(Locale.ROOT);

            String value =
                    declaration
                            .substring(colon + 1)
                            .trim();

            if (value.isBlank()) {
                continue;
            }

            switch (property) {

                case "color" -> {

                    if (isSafeColor(value)) {

                        safeStyles.put(
                                "color",
                                normalizeCssValue(value)
                        );
                    }
                }

                case "background-color" -> {

                    if (isSafeColor(value)) {

                        safeStyles.put(
                                "background-color",
                                normalizeCssValue(value)
                        );
                    }
                }

                /*
                 * Keep compatibility if the frontend later
                 * configures Quill style attributors rather
                 * than class attributors.
                 */
                case "text-align" -> {

                    String normalized =
                            value
                                    .toLowerCase(
                                            Locale.ROOT
                                    );

                    if (
                            normalized.equals("left")
                                    || normalized.equals("center")
                                    || normalized.equals("right")
                                    || normalized.equals("justify")
                    ) {

                        safeStyles.put(
                                "text-align",
                                normalized
                        );
                    }
                }

                case "direction" -> {

                    String normalized =
                            value
                                    .toLowerCase(
                                            Locale.ROOT
                                    );

                    if (
                            normalized.equals("ltr")
                                    || normalized.equals("rtl")
                    ) {

                        safeStyles.put(
                                "direction",
                                normalized
                        );
                    }
                }

                case "font-size" -> {

                    String normalized =
                            sanitizeFontSize(value);

                    if (normalized != null) {

                        safeStyles.put(
                                "font-size",
                                normalized
                        );
                    }
                }

                case "font-family" -> {

                    String normalized =
                            sanitizeFontFamily(value);

                    if (normalized != null) {

                        safeStyles.put(
                                "font-family",
                                normalized
                        );
                    }
                }

                default -> {
                    /*
                     * Reject every other CSS property.
                     */
                }
            }
        }

        if (safeStyles.isEmpty()) {

            element.removeAttr("style");
            return;
        }

        StringBuilder rebuilt =
                new StringBuilder();

        for (
                Map.Entry<String, String> entry
                : safeStyles.entrySet()
        ) {

            if (!rebuilt.isEmpty()) {
                rebuilt.append("; ");
            }

            rebuilt
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue());
        }

        element.attr(
                "style",
                rebuilt.toString()
        );
    }

    private static boolean isSafeColor(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return false;
        }

        String normalized =
                value
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (
                NAMED_COLORS
                        .contains(normalized)
        ) {
            return true;
        }

        if (
                HEX_COLOR
                        .matcher(normalized)
                        .matches()
        ) {
            return true;
        }

        Matcher rgbMatcher =
                RGB_COLOR.matcher(normalized);

        if (!rgbMatcher.matches()) {
            return false;
        }

        for (int i = 1; i <= 3; i++) {

            int component =
                    Integer.parseInt(
                            rgbMatcher.group(i)
                    );

            if (
                    component < 0
                            || component > 255
            ) {
                return false;
            }
        }

        return true;
    }

    private static String normalizeCssValue(
            String value
    ) {

        return value
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    private static String sanitizeFontSize(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
                value
                        .trim()
                        .toLowerCase(Locale.ROOT);

        /*
         * Bounded values prevent oversized text from
         * destroying the invoice layout.
         */
        if (
                normalized.matches(
                        "^(6|7|8|9|1[0-9]|2[0-9]|3[0-6])px$"
                )
        ) {
            return normalized;
        }

        if (
                normalized.matches(
                        "^([5-9]|1[0-9]|2[0-7])pt$"
                )
        ) {
            return normalized;
        }

        if (
                normalized.matches(
                        "^(0\\.[5-9]|[12](\\.\\d)?|3(\\.0)?)em$"
                )
        ) {
            return normalized;
        }

        if (
                normalized.matches(
                        "^(5[0-9]|[6-9][0-9]|1[0-9]{2}|2[0-9]{2}|300)%$"
                )
        ) {
            return normalized;
        }

        return null;
    }

    private static String sanitizeFontFamily(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
                value
                        .trim()
                        .replace("\"", "")
                        .replace("'", "")
                        .toLowerCase(Locale.ROOT);

        return switch (normalized) {

            case "montserrat",
                 "sans-serif" ->
                    "'Montserrat', sans-serif";

            case "serif",
                 "times",
                 "times new roman" ->
                    "serif";

            case "monospace",
                 "courier",
                 "courier new" ->
                    "monospace";

            default -> null;
        };
    }

    private static void sanitizeDirection(
            Element element
    ) {

        if (!element.hasAttr("dir")) {
            return;
        }

        String value =
                element
                        .attr("dir")
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (
                value.equals("ltr")
                        || value.equals("rtl")
        ) {

            element.attr(
                    "dir",
                    value
            );

        } else {

            element.removeAttr("dir");
        }
    }

    private static void sanitizeListType(
            Element element
    ) {

        if (!element.hasAttr("data-list")) {
            return;
        }

        if (!element.tagName().equals("li")) {

            element.removeAttr("data-list");
            return;
        }

        String value =
                element
                        .attr("data-list")
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (
                ALLOWED_DATA_LIST_VALUES
                        .contains(value)
        ) {

            element.attr(
                    "data-list",
                    value
            );

        } else {

            element.removeAttr("data-list");
        }
    }

    private static boolean isEffectivelyEmpty(
            String html
    ) {

        if (
                html == null
                        || html.isBlank()
        ) {
            return true;
        }

        Document document =
                Jsoup.parseBodyFragment(html);

        /*
         * Treat Quill's normal empty value:
         *
         * <p><br></p>
         *
         * as empty invoice terms.
         */
        return document
                .body()
                .text()
                .trim()
                .isEmpty();
    }
}
package com.core.dataexchange.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;

@Component
public class CsvCodec {

    private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public List<CsvRow> parse(MultipartFile file, List<String> expectedHeaders,
                              long maxFileSizeBytes, int maxRows) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV file is required.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "CSV file exceeds the maximum allowed size of " + maxFileSizeBytes + " bytes.");
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only .csv files are supported.");
        }

        try {
            byte[] bytes = file.getBytes();
            int offset = hasBom(bytes) ? 3 : 0;
            String csv = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(false)
                    .build();

            try (CSVParser parser = new CSVParser(new StringReader(csv), format)) {
                List<String> headers = parser.getHeaderNames();
                validateHeaders(headers, expectedHeaders);

                List<CsvRow> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    if (record.size() != headers.size()) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "Malformed CSV row at line " + record.getRecordNumber() + ".");
                    }
                    if (rows.size() >= maxRows) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "CSV exceeds the maximum row limit of " + maxRows + ".");
                    }
                    Map<String, String> values = new LinkedHashMap<>();
                    boolean nonEmpty = false;
                    for (String header : expectedHeaders) {
                        String value = normalizeImportedCell(record.get(header));
                        values.put(header, value);
                        nonEmpty = nonEmpty || (value != null && !value.isBlank());
                    }
                    if (nonEmpty) {
                        rows.add(new CsvRow(record.getRecordNumber() + 1, values));
                    }
                }
                if (rows.isEmpty()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV does not contain any data rows.");
                }
                return rows;
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to read CSV: " + ex.getMessage());
        }
    }

    public byte[] write(List<String> headers, List<Map<String, String>> rows) {
        try {
            StringWriter writer = new StringWriter();
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader(headers.toArray(String[]::new))
                    .setRecordSeparator("\r\n")
                    .build();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (Map<String, String> row : rows) {
                    List<String> values = headers.stream()
                            .map(header -> sanitizeForExport(row.get(header)))
                            .toList();
                    printer.printRecord(values);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(UTF8_BOM);
            output.write(writer.toString().getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate CSV.", ex);
        }
    }

    private void validateHeaders(List<String> actual, List<String> expected) {
        if (actual == null || actual.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV header row is missing.");
        }
        Set<String> unique = new HashSet<>(actual);
        if (unique.size() != actual.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV contains duplicate column names.");
        }
        if (!actual.equals(expected)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "CSV columns do not match the current template. Download a fresh template or current-data export.");
        }
    }

    private boolean hasBom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
    }

    private String sanitizeForExport(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private String normalizeImportedCell(String value) {
        if (value != null && value.length() >= 2 && value.charAt(0) == '\''
                && "=+-@".indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }
}

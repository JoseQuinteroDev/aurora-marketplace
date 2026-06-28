package com.aurora.backend.batch.support;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.aurora.backend.batch.dto.InventorySyncLine;
import com.aurora.backend.batch.dto.ProductImportLine;

import org.springframework.stereotype.Component;

@Component
public class BatchFileReader {

    /**
     * Availability guards (OWASP A04): a batch CSV is read line-by-line and iterated inside a single
     * {@code @Transactional} tasklet, so an oversized file would otherwise pull unbounded data into heap
     * and can OOM the JVM. We cap both the on-disk byte size and the parsed row count before/while reading.
     */
    static final long MAX_FILE_BYTES = 50L * 1024 * 1024; // 50 MiB
    static final int MAX_ROWS = 100_000;

    public List<ProductImportLine> readProductImportLines(Path file) throws IOException {
        return readCapped(file, line -> !line.toLowerCase().startsWith("name,"), this::toProductImportLine);
    }

    public List<InventorySyncLine> readInventorySyncLines(Path file) throws IOException {
        return readCapped(file, line -> !line.toLowerCase().startsWith("sku,"), this::toInventorySyncLine);
    }

    /**
     * Streams the file (so the raw text is never fully buffered), enforcing the byte-size cap up front and
     * the row-count cap as parsed lines accumulate. Header/blank lines are skipped and do not count as rows.
     */
    private <T> List<T> readCapped(Path file, Predicate<String> notHeader, Function<String, T> mapper)
            throws IOException {
        requireWithinSizeCap(file);

        List<T> rows = new ArrayList<>();
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank())
                    .filter(notHeader)
                    .forEach(line -> {
                        if (rows.size() >= MAX_ROWS) {
                            throw new IllegalStateException(
                                    "Batch CSV exceeds the maximum of " + MAX_ROWS + " data rows.");
                        }
                        rows.add(mapper.apply(line));
                    });
        }
        return rows;
    }

    private void requireWithinSizeCap(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            throw new IllegalStateException(
                    "Batch CSV is " + size + " bytes, exceeding the maximum of " + MAX_FILE_BYTES + " bytes.");
        }
    }

    private ProductImportLine toProductImportLine(String line) {
        String[] values = line.split(",", -1);
        requireColumnCount(values, 15, "product import");

        return new ProductImportLine(
                values[0].trim(),
                values[1].trim(),
                blankToNull(values[2]),
                blankToNull(values[3]),
                new BigDecimal(values[4].trim()),
                values[5].trim(),
                values[6].trim(),
                values[7].trim(),
                values[8].trim(),
                values[9].trim(),
                values[10].trim(),
                blankToBigDecimal(values[11]),
                blankToNull(values[12]),
                parseBoolean(values[13], true),
                parseBoolean(values[14], false)
        );
    }

    private InventorySyncLine toInventorySyncLine(String line) {
        String[] values = line.split(",", -1);
        requireColumnCount(values, 2, "inventory sync");

        Integer lowStockThreshold = values.length > 2 && !values[2].isBlank()
                ? Integer.valueOf(values[2].trim())
                : null;

        return new InventorySyncLine(values[0].trim(), Integer.parseInt(values[1].trim()), lowStockThreshold);
    }

    private void requireColumnCount(String[] values, int expected, String fileType) {
        if (values.length < expected) {
            throw new IllegalArgumentException("Invalid " + fileType + " CSV line. Expected at least " + expected + " columns.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal blankToBigDecimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value.trim());
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Boolean.parseBoolean(value.trim());
    }
}

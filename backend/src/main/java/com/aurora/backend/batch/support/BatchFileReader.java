package com.aurora.backend.batch.support;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aurora.backend.batch.dto.InventorySyncLine;
import com.aurora.backend.batch.dto.ProductImportLine;

import org.springframework.stereotype.Component;

@Component
public class BatchFileReader {

    public List<ProductImportLine> readProductImportLines(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.toLowerCase().startsWith("name,"))
                .map(this::toProductImportLine)
                .toList();
    }

    public List<InventorySyncLine> readInventorySyncLines(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.toLowerCase().startsWith("sku,"))
                .map(this::toInventorySyncLine)
                .toList();
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

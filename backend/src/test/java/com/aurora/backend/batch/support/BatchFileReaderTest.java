package com.aurora.backend.batch.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.aurora.backend.batch.dto.InventorySyncLine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Availability guards (OWASP A04): an oversized batch CSV must be rejected before it is fully read into heap
 * and iterated inside a {@code @Transactional} tasklet. These tests fail if the byte-size or row-count cap
 * is removed.
 */
class BatchFileReaderTest {

    private final BatchFileReader reader = new BatchFileReader();
    private Path tempFile;

    @AfterEach
    void cleanUp() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void readsANormalSmallInventoryFile() throws IOException {
        tempFile = Files.createTempFile("inventory-sync", ".csv");
        Files.writeString(tempFile, "sku,quantity,lowStockThreshold\nSKU-1,5\nSKU-2,9,2\n", StandardCharsets.UTF_8);

        var lines = reader.readInventorySyncLines(tempFile);

        assertThat(lines).containsExactly(
                new InventorySyncLine("SKU-1", 5, null),
                new InventorySyncLine("SKU-2", 9, 2));
    }

    @Test
    void rejectsAFileLargerThanTheByteCap() throws IOException {
        tempFile = Files.createTempFile("inventory-sync-oversize", ".csv");
        // One byte over the cap is enough to trip the Files.size() guard before any line is read.
        byte[] oversize = new byte[(int) (BatchFileReader.MAX_FILE_BYTES + 1)];
        Arrays.fill(oversize, (byte) 'a');
        Files.write(tempFile, oversize);

        assertThatThrownBy(() -> reader.readInventorySyncLines(tempFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(BatchFileReader.MAX_FILE_BYTES));
    }

    @Test
    void rejectsAFileWithMoreRowsThanTheRowCap() throws IOException {
        tempFile = Files.createTempFile("inventory-sync-toomany", ".csv");
        // Header line does not count as a data row; write one more valid row than the cap allows.
        StringBuilder csv = new StringBuilder("sku,quantity\n");
        for (int i = 0; i <= BatchFileReader.MAX_ROWS; i++) {
            csv.append("SKU-").append(i).append(",1\n");
        }
        Files.writeString(tempFile, csv.toString(), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.readInventorySyncLines(tempFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(BatchFileReader.MAX_ROWS));
    }
}

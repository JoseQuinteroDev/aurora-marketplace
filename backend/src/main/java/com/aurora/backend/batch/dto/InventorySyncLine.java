package com.aurora.backend.batch.dto;

public record InventorySyncLine(
        String sku,
        int availableQuantity,
        Integer lowStockThreshold
) {
}

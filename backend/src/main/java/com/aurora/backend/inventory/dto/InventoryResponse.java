package com.aurora.backend.inventory.dto;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.inventory.entity.Inventory;

public record InventoryResponse(
        UUID id,
        UUID variantId,
        String sku,
        String variantName,
        UUID productId,
        String productName,
        int availableQuantity,
        int reservedQuantity,
        int lowStockThreshold,
        boolean lowStock,
        Instant updatedAt
) {

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getVariant().getId(),
                inventory.getVariant().getSku(),
                inventory.getVariant().getName(),
                inventory.getVariant().getProduct().getId(),
                inventory.getVariant().getProduct().getName(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getLowStockThreshold(),
                inventory.getAvailableQuantity() <= inventory.getLowStockThreshold(),
                inventory.getUpdatedAt()
        );
    }
}

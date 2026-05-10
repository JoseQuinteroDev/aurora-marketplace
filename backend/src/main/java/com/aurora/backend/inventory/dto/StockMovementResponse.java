package com.aurora.backend.inventory.dto;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.inventory.entity.StockMovement;
import com.aurora.backend.inventory.entity.StockMovementType;

public record StockMovementResponse(
        UUID id,
        UUID variantId,
        String sku,
        StockMovementType type,
        int quantity,
        String reason,
        Instant createdAt
) {

    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getVariant().getId(),
                movement.getVariant().getSku(),
                movement.getType(),
                movement.getQuantity(),
                movement.getReason(),
                movement.getCreatedAt()
        );
    }
}

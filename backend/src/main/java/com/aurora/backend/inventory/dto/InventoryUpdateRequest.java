package com.aurora.backend.inventory.dto;

import jakarta.validation.constraints.Min;

public record InventoryUpdateRequest(
        @Min(value = 0, message = "Available quantity cannot be negative.")
        Integer availableQuantity,

        @Min(value = 0, message = "Reserved quantity cannot be negative.")
        Integer reservedQuantity,

        @Min(value = 0, message = "Low stock threshold cannot be negative.")
        Integer lowStockThreshold
) {
}

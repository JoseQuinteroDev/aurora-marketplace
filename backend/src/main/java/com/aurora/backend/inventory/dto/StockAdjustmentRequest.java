package com.aurora.backend.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @Min(value = 0, message = "Available quantity cannot be negative.")
        Integer availableQuantity,

        @Min(value = 0, message = "Reserved quantity cannot be negative.")
        Integer reservedQuantity,

        @Min(value = 0, message = "Low stock threshold cannot be negative.")
        Integer lowStockThreshold,

        @Size(max = 255, message = "Reason must be at most 255 characters.")
        String reason
) {
}

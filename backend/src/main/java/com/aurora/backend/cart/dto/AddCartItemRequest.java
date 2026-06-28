package com.aurora.backend.cart.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull(message = "Variant id is required.")
        UUID variantId,

        @Min(value = 1, message = "Quantity must be greater than zero.")
        @Max(value = 1000, message = "Quantity must be at most 1000.")
        int quantity
) {
}

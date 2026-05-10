package com.aurora.backend.cart.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull(message = "Variant id is required.")
        UUID variantId,

        @Min(value = 1, message = "Quantity must be greater than zero.")
        int quantity
) {
}

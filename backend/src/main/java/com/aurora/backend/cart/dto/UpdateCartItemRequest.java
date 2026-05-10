package com.aurora.backend.cart.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
        @Min(value = 1, message = "Quantity must be greater than zero.")
        int quantity
) {
}

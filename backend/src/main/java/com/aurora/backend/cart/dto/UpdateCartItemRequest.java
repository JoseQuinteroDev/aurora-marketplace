package com.aurora.backend.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
        @Min(value = 1, message = "Quantity must be greater than zero.")
        @Max(value = 1000, message = "Quantity must be at most 1000.")
        int quantity
) {
}

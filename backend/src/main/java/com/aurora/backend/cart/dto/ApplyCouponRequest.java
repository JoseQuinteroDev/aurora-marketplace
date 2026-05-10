package com.aurora.backend.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyCouponRequest(
        @NotBlank(message = "Coupon code is required.")
        @Size(max = 80, message = "Coupon code must be at most 80 characters.")
        String code
) {
}

package com.aurora.backend.promotion.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.aurora.backend.promotion.entity.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CouponRequest(
        @NotBlank(message = "Coupon code is required.")
        @Size(max = 80, message = "Coupon code must be at most 80 characters.")
        String code,

        @NotNull(message = "Coupon type is required.")
        CouponType type,

        @NotNull(message = "Coupon value is required.")
        @DecimalMin(value = "0.01", message = "Coupon value must be greater than zero.")
        BigDecimal value,

        Boolean active,

        Instant startsAt,

        Instant endsAt,

        @Min(value = 1, message = "Maximum uses must be at least 1.")
        Integer maxUses,

        @Min(value = 1, message = "Maximum uses per user must be at least 1.")
        Integer maxUsesPerUser,

        @DecimalMin(value = "0.00", message = "Minimum order amount must be zero or greater.")
        BigDecimal minimumOrderAmount
) {
}

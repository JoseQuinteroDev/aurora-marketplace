package com.aurora.backend.promotion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.promotion.entity.Coupon;
import com.aurora.backend.promotion.entity.CouponType;

public record CouponResponse(
        UUID id,
        String code,
        CouponType type,
        BigDecimal value,
        boolean active,
        Instant startsAt,
        Instant endsAt,
        Integer maxUses,
        Integer maxUsesPerUser,
        BigDecimal minimumOrderAmount
) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getType(),
                coupon.getValue(),
                coupon.isActive(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.getMaxUses(),
                coupon.getMaxUsesPerUser(),
                coupon.getMinimumOrderAmount()
        );
    }
}

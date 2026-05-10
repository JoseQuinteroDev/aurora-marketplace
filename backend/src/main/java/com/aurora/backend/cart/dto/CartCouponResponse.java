package com.aurora.backend.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.aurora.backend.promotion.entity.Coupon;
import com.aurora.backend.promotion.entity.CouponType;

public record CartCouponResponse(
        UUID id,
        String code,
        CouponType type,
        BigDecimal value
) {

    public static CartCouponResponse from(Coupon coupon) {
        return new CartCouponResponse(coupon.getId(), coupon.getCode(), coupon.getType(), coupon.getValue());
    }
}

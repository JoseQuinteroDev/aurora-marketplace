package com.aurora.backend.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        List<CartItemResponse> items,
        CartCouponResponse coupon,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal total
) {
}

package com.aurora.backend.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSlug,
        UUID variantId,
        String variantSku,
        String variantName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}

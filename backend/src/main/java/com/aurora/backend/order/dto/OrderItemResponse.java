package com.aurora.backend.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.aurora.backend.order.entity.OrderItem;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        UUID variantId,
        String productName,
        String productSlug,
        String variantSku,
        String variantName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getVariantId(),
                item.getProductName(),
                item.getProductSlug(),
                item.getVariantSku(),
                item.getVariantName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal()
        );
    }
}

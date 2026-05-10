package com.aurora.backend.wishlist.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.wishlist.entity.WishlistItem;

public record WishlistItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSlug,
        BigDecimal basePrice,
        Instant createdAt
) {

    public static WishlistItemResponse from(WishlistItem item) {
        Product product = item.getProduct();
        return new WishlistItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getBasePrice(),
                item.getCreatedAt()
        );
    }
}

package com.aurora.backend.catalog.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.ProductVariant;

public record ProductVariantResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal priceOverride,
        BigDecimal effectivePrice,
        String attributesJson,
        boolean active
) {

    public static ProductVariantResponse from(ProductVariant variant, BigDecimal productBasePrice) {
        BigDecimal effectivePrice = variant.getPriceOverride() == null
                ? productBasePrice
                : variant.getPriceOverride();

        return new ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPriceOverride(),
                effectivePrice,
                variant.getAttributesJson(),
                variant.isActive()
        );
    }
}

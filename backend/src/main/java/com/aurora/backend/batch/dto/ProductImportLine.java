package com.aurora.backend.batch.dto;

import java.math.BigDecimal;

public record ProductImportLine(
        String name,
        String slug,
        String description,
        String shortDescription,
        BigDecimal basePrice,
        String categorySlug,
        String categoryName,
        String brandSlug,
        String brandName,
        String sku,
        String variantName,
        BigDecimal priceOverride,
        String attributesJson,
        boolean active,
        boolean featured
) {
}

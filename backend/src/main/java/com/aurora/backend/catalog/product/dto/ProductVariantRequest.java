package com.aurora.backend.catalog.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductVariantRequest(
        UUID id,

        @NotBlank(message = "Variant SKU is required.")
        @Size(max = 80, message = "Variant SKU must be at most 80 characters.")
        String sku,

        @NotBlank(message = "Variant name is required.")
        @Size(max = 180, message = "Variant name must be at most 180 characters.")
        String name,

        @DecimalMin(value = "0.00", message = "Variant price override must be zero or greater.")
        BigDecimal priceOverride,

        String attributesJson,

        Boolean active
) {
}

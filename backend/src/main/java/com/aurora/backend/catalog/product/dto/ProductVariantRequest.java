package com.aurora.backend.catalog.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
        @Digits(integer = 10, fraction = 2, message = "Variant price override must have at most 10 integer and 2 fraction digits.")
        BigDecimal priceOverride,

        @Size(max = 2000, message = "Variant attributes JSON must be at most 2000 characters.")
        String attributesJson,

        Boolean active
) {
}

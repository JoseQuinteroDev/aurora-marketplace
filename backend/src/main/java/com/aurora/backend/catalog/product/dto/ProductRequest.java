package com.aurora.backend.catalog.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "Product name is required.")
        @Size(max = 180, message = "Product name must be at most 180 characters.")
        String name,

        @Size(max = 220, message = "Product slug must be at most 220 characters.")
        String slug,

        String description,

        @Size(max = 500, message = "Short description must be at most 500 characters.")
        String shortDescription,

        @NotNull(message = "Base price is required.")
        @DecimalMin(value = "0.00", message = "Base price must be zero or greater.")
        BigDecimal basePrice,

        Boolean active,

        Boolean featured,

        @NotNull(message = "Category id is required.")
        UUID categoryId,

        @NotNull(message = "Brand id is required.")
        UUID brandId,

        @Valid
        List<ProductVariantRequest> variants,

        @Valid
        List<ProductImageRequest> images
) {
}

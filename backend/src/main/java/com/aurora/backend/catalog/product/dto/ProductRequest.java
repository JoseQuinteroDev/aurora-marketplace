package com.aurora.backend.catalog.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "Product name is required.")
        @Size(max = 180, message = "Product name must be at most 180 characters.")
        String name,

        @Size(max = 220, message = "Product slug must be at most 220 characters.")
        String slug,

        @Size(max = 5000, message = "Description must be at most 5000 characters.")
        String description,

        @Size(max = 500, message = "Short description must be at most 500 characters.")
        String shortDescription,

        @NotNull(message = "Base price is required.")
        @DecimalMin(value = "0.00", message = "Base price must be zero or greater.")
        @Digits(integer = 10, fraction = 2, message = "Base price must have at most 10 integer and 2 fraction digits.")
        BigDecimal basePrice,

        Boolean active,

        Boolean featured,

        @NotNull(message = "Category id is required.")
        UUID categoryId,

        @NotNull(message = "Brand id is required.")
        UUID brandId,

        @Valid
        @Size(max = 100, message = "A product cannot have more than 100 variants.")
        List<ProductVariantRequest> variants,

        @Valid
        @Size(max = 50, message = "A product cannot have more than 50 images.")
        List<ProductImageRequest> images
) {
}

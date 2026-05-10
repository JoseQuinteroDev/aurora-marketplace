package com.aurora.backend.catalog.brand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandRequest(
        @NotBlank(message = "Brand name is required.")
        @Size(max = 120, message = "Brand name must be at most 120 characters.")
        String name,

        @Size(max = 160, message = "Brand slug must be at most 160 characters.")
        String slug,

        Boolean active
) {
}

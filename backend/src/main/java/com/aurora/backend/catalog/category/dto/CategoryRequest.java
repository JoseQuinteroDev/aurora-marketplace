package com.aurora.backend.catalog.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Category name is required.")
        @Size(max = 120, message = "Category name must be at most 120 characters.")
        String name,

        @Size(max = 160, message = "Category slug must be at most 160 characters.")
        String slug,

        Boolean active
) {
}

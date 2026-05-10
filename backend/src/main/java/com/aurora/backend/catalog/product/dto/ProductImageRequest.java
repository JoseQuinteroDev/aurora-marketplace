package com.aurora.backend.catalog.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageRequest(
        @NotBlank(message = "Image URL is required.")
        @Size(max = 1000, message = "Image URL must be at most 1000 characters.")
        String url,

        @Size(max = 255, message = "Image alt text must be at most 255 characters.")
        String altText,

        @Min(value = 0, message = "Image position must be zero or greater.")
        Integer position,

        Boolean mainImage
) {
}

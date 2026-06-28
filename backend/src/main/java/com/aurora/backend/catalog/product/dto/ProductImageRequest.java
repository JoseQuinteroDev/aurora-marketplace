package com.aurora.backend.catalog.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductImageRequest(
        @NotBlank(message = "Image URL is required.")
        @Size(max = 1000, message = "Image URL must be at most 1000 characters.")
        // Defense in depth (OWASP A03/A10): image URLs are stored and rendered by the browser,
        // never fetched server-side, so this is not a live SSRF sink — but constraining stored
        // URLs to absolute http(s) blocks javascript:/data:/file: scheme abuse (stored-XSS via a
        // rendered attribute) and is the input guard a future server-side fetch would build on.
        @Pattern(
                regexp = "^https?://.+",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Image URL must be an absolute http(s) URL."
        )
        String url,

        @Size(max = 255, message = "Image alt text must be at most 255 characters.")
        String altText,

        @Min(value = 0, message = "Image position must be zero or greater.")
        Integer position,

        Boolean mainImage
) {
}

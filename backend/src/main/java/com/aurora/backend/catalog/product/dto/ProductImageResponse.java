package com.aurora.backend.catalog.product.dto;

import java.util.UUID;

import com.aurora.backend.catalog.product.entity.ProductImage;

public record ProductImageResponse(
        UUID id,
        String url,
        String altText,
        int position,
        boolean mainImage
) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getUrl(),
                image.getAltText(),
                image.getPosition(),
                image.isMainImage()
        );
    }
}

package com.aurora.backend.catalog.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.aurora.backend.catalog.brand.dto.BrandResponse;
import com.aurora.backend.catalog.category.dto.CategoryResponse;
import com.aurora.backend.catalog.product.entity.Product;

public record ProductResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String shortDescription,
        BigDecimal basePrice,
        boolean active,
        boolean featured,
        CategoryResponse category,
        BrandResponse brand,
        List<ProductVariantResponse> variants,
        List<ProductImageResponse> images,
        Double averageRating,
        long reviewCount
) {

    /** No rating context (write responses / admin) — count 0, average null. */
    public static ProductResponse from(Product product) {
        return from(product, null, 0L);
    }

    public static ProductResponse from(Product product, Double averageRating, long reviewCount) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getShortDescription(),
                product.getBasePrice(),
                product.isActive(),
                product.isFeatured(),
                CategoryResponse.from(product.getCategory()),
                BrandResponse.from(product.getBrand()),
                product.getVariants().stream()
                        .map(variant -> ProductVariantResponse.from(variant, product.getBasePrice()))
                        .toList(),
                product.getImages().stream()
                        .map(ProductImageResponse::from)
                        .toList(),
                averageRating,
                reviewCount
        );
    }
}
